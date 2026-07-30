package com.shopmanagement.crmservice.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.shopmanagement.crmservice.api.CrmDealApi.QuotationResponse;
import com.shopmanagement.crmservice.api.CrmDealApi.QuotationUpsert;
import com.shopmanagement.crmservice.api.CrmDealApi.QuoteLine;
import com.shopmanagement.crmservice.persistence.entity.CrmOpportunityEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmQuotationEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmOpportunityRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmQuotationRepository;
import com.shopmanagement.crmservice.support.TenantIds;

@Service
public class QuotationService {

  private final CrmQuotationRepository quotationRepository;
  private final CrmOpportunityRepository opportunityRepository;
  private final TimelineService timelineService;

  public QuotationService(
      CrmQuotationRepository quotationRepository,
      CrmOpportunityRepository opportunityRepository,
      TimelineService timelineService) {
    this.quotationRepository = quotationRepository;
    this.opportunityRepository = opportunityRepository;
    this.timelineService = timelineService;
  }

  @Transactional
  public QuotationResponse create(QuotationUpsert body) {
    String tenantId = TenantIds.require();
    CrmOpportunityEntity opp =
        opportunityRepository
            .findByTenantIdAndIdAndDeletedAtIsNull(tenantId, body.opportunityId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid opportunityId"));

    CrmQuotationEntity quote = new CrmQuotationEntity();
    quote.setTenantId(tenantId);
    quote.setOpportunityId(opp.getId());
    long seq = quotationRepository.countByTenantIdAndDeletedAtIsNull(tenantId) + 1;
    quote.setQuoteNumber(String.format(Locale.ROOT, "Q-%s-%04d", tenantId.replaceAll("[^A-Za-z0-9]", ""), seq));
    quote.setVersionNo(1);
    quote.setStatus("DRAFT");
    applyHeader(quote, body);
    applyTotals(quote, body.lines() == null ? List.of() : body.lines(), body.discountAmount());
    quote.setSharePayloadJson(buildSharePayload(quote));
    quote = quotationRepository.save(quote);

    timelineService.recordEvent(
        "OPPORTUNITY",
        opp.getId(),
        "QUOTE_CREATED",
        "Quote " + quote.getQuoteNumber() + " created",
        Map.of("quotationId", quote.getId(), "total", quote.getTotalAmount()));
    return toResponse(quote);
  }

  @Transactional
  public QuotationResponse markSent(Long id) {
    CrmQuotationEntity quote = require(TenantIds.require(), id);
    quote.setStatus("SENT");
    quote.setSharePayloadJson(buildSharePayload(quote));
    quote.touch();
    quote = quotationRepository.save(quote);
    timelineService.recordEvent(
        "OPPORTUNITY",
        quote.getOpportunityId(),
        "QUOTE_SENT",
        "Quote " + quote.getQuoteNumber() + " marked SENT",
        Map.of("quotationId", quote.getId()));
    return toResponse(quote);
  }

  @Transactional
  public QuotationResponse accept(Long id) {
    CrmQuotationEntity quote = require(TenantIds.require(), id);
    quote.setStatus("ACCEPTED");
    quote.setAcceptedAt(java.time.Instant.now());
    quote.touch();
    quote = quotationRepository.save(quote);
    timelineService.recordEvent(
        "OPPORTUNITY",
        quote.getOpportunityId(),
        "QUOTE_ACCEPTED",
        "Quote " + quote.getQuoteNumber() + " accepted",
        Map.of("quotationId", quote.getId()));
    return toResponse(quote);
  }

  @Transactional(readOnly = true)
  public QuotationResponse get(Long id) {
    return toResponse(require(TenantIds.require(), id));
  }

  @Transactional(readOnly = true)
  public List<QuotationResponse> listForOpportunity(Long opportunityId) {
    String tenantId = TenantIds.require();
    return quotationRepository
        .findByTenantIdAndOpportunityIdAndDeletedAtIsNullOrderByVersionNoDesc(tenantId, opportunityId)
        .stream()
        .map(QuotationService::toResponse)
        .toList();
  }

  private CrmQuotationEntity require(String tenantId, Long id) {
    return quotationRepository
        .findByTenantIdAndIdAndDeletedAtIsNull(tenantId, id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quotation not found"));
  }

  private void applyHeader(CrmQuotationEntity quote, QuotationUpsert body) {
    quote.setCustomerName(blankToNull(body.customerName()));
    quote.setCustomerGstin(blankToNull(body.customerGstin()));
    quote.setPlaceOfSupply(blankToNull(body.placeOfSupply()));
    quote.setSellerStateCode(normalizeState(body.sellerStateCode()));
    quote.setBuyerStateCode(normalizeState(body.buyerStateCode()));
    if (body.currency() != null && !body.currency().isBlank()) {
      quote.setCurrency(body.currency().trim().toUpperCase(Locale.ROOT));
    }
    quote.setTerms(blankToNull(body.terms()));
    quote.setValidUntil(body.validUntil());
  }

  private void applyTotals(CrmQuotationEntity quote, List<QuoteLine> lines, BigDecimal headerDiscount) {
    BigDecimal taxable = BigDecimal.ZERO;
    BigDecimal cgst = BigDecimal.ZERO;
    BigDecimal sgst = BigDecimal.ZERO;
    BigDecimal igst = BigDecimal.ZERO;
    List<Map<String, Object>> lineMaps = new ArrayList<>();

    boolean intra =
        quote.getSellerStateCode() != null
            && quote.getSellerStateCode().equalsIgnoreCase(quote.getBuyerStateCode());

    for (QuoteLine line : lines) {
      BigDecimal qty = nvl(line.qty());
      BigDecimal price = nvl(line.unitPrice());
      BigDecimal disc = nvl(line.discount());
      BigDecimal rate = nvl(line.gstRate());
      BigDecimal lineTaxable = qty.multiply(price).subtract(disc).max(BigDecimal.ZERO);
      BigDecimal tax = lineTaxable.multiply(rate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
      if (intra) {
        BigDecimal half = tax.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        cgst = cgst.add(half);
        sgst = sgst.add(half);
      } else {
        igst = igst.add(tax);
      }
      taxable = taxable.add(lineTaxable);

      Map<String, Object> m = new LinkedHashMap<>();
      m.put("description", line.description());
      m.put("hsn", line.hsn());
      m.put("qty", qty);
      m.put("unitPrice", price);
      m.put("gstRate", rate);
      m.put("discount", disc);
      m.put("taxable", lineTaxable);
      m.put("tax", tax);
      lineMaps.add(m);
    }

    BigDecimal discount = nvl(headerDiscount);
    taxable = taxable.subtract(discount).max(BigDecimal.ZERO);
    quote.setDiscountAmount(discount.setScale(2, RoundingMode.HALF_UP));
    quote.setTaxableAmount(taxable.setScale(2, RoundingMode.HALF_UP));
    quote.setCgstAmount(cgst.setScale(2, RoundingMode.HALF_UP));
    quote.setSgstAmount(sgst.setScale(2, RoundingMode.HALF_UP));
    quote.setIgstAmount(igst.setScale(2, RoundingMode.HALF_UP));
    quote.setTotalAmount(
        taxable.add(cgst).add(sgst).add(igst).setScale(2, RoundingMode.HALF_UP));
    quote.setLinesJson(lineMaps);
  }

  private Map<String, Object> buildSharePayload(CrmQuotationEntity quote) {
    Map<String, Object> payload = new LinkedHashMap<>();
    String text =
        "Quotation "
            + quote.getQuoteNumber()
            + "\nCustomer: "
            + Objects.toString(quote.getCustomerName(), "-")
            + "\nGSTIN: "
            + Objects.toString(quote.getCustomerGstin(), "-")
            + "\nTaxable: ₹"
            + quote.getTaxableAmount()
            + "\nCGST: ₹"
            + quote.getCgstAmount()
            + " | SGST: ₹"
            + quote.getSgstAmount()
            + " | IGST: ₹"
            + quote.getIgstAmount()
            + "\nTotal: ₹"
            + quote.getTotalAmount()
            + "\n(SugamFlow CRM)";
    payload.put("whatsappText", text);
    payload.put("emailSubject", "Quotation " + quote.getQuoteNumber());
    payload.put("emailBody", text);
    payload.put("channelHints", List.of("WHATSAPP", "EMAIL"));
    payload.put("note", "Phase 2: share payload ready — wire notification-service in next slice");
    return payload;
  }

  private static BigDecimal nvl(BigDecimal v) {
    return v == null ? BigDecimal.ZERO : v;
  }

  private static String blankToNull(String v) {
    return v == null || v.isBlank() ? null : v.trim();
  }

  private static String normalizeState(String v) {
    String s = blankToNull(v);
    return s == null ? null : s.toUpperCase(Locale.ROOT);
  }

  private static QuotationResponse toResponse(CrmQuotationEntity q) {
    return new QuotationResponse(
        q.getId(),
        q.getOpportunityId(),
        q.getQuoteNumber(),
        q.getVersionNo(),
        q.getStatus(),
        q.getCustomerName(),
        q.getCustomerGstin(),
        q.getPlaceOfSupply(),
        q.getSellerStateCode(),
        q.getBuyerStateCode(),
        q.getCurrency(),
        q.getTaxableAmount(),
        q.getCgstAmount(),
        q.getSgstAmount(),
        q.getIgstAmount(),
        q.getTotalAmount(),
        q.getDiscountAmount(),
        q.getTerms(),
        q.getLinesJson(),
        q.getSharePayloadJson(),
        q.getValidUntil(),
        q.getAcceptedAt(),
        q.getCreatedAt());
  }
}
