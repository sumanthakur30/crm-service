package com.shopmanagement.crmservice.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.shopmanagement.crmservice.api.CrmDealApi.QuotationResponse;
import com.shopmanagement.crmservice.api.CrmDealApi.QuotationSendRequest;
import com.shopmanagement.crmservice.api.CrmDealApi.QuotationUpsert;
import com.shopmanagement.crmservice.api.CrmDealApi.QuoteLine;
import com.shopmanagement.crmservice.config.CrmQuoteProperties;
import com.shopmanagement.crmservice.integration.NotificationClient;
import com.shopmanagement.crmservice.integration.OrderClient;
import com.shopmanagement.crmservice.payment.PaymentLinkProvider;
import com.shopmanagement.crmservice.payment.PaymentLinkResult;
import com.shopmanagement.crmservice.persistence.entity.CrmLeadEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmOpportunityEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmQuotationEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmLeadRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmOpportunityRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmQuotationRepository;
import com.shopmanagement.crmservice.support.TenantIds;

@Service
public class QuotationService {

  private static final Set<String> SEND_CHANNELS = Set.of("EMAIL", "WHATSAPP", "SMS");

  private final CrmQuotationRepository quotationRepository;
  private final CrmOpportunityRepository opportunityRepository;
  private final CrmLeadRepository leadRepository;
  private final TimelineService timelineService;
  private final NotificationClient notificationClient;
  private final OrderClient orderClient;
  private final PaymentLinkProvider paymentLinkProvider;
  private final BehaviorScoringService scoringService;
  private final CrmQuoteProperties quoteProperties;
  private final QuoteDiscountApprovalSupport discountApprovalSupport;

  public QuotationService(
      CrmQuotationRepository quotationRepository,
      CrmOpportunityRepository opportunityRepository,
      CrmLeadRepository leadRepository,
      TimelineService timelineService,
      NotificationClient notificationClient,
      OrderClient orderClient,
      PaymentLinkProvider paymentLinkProvider,
      BehaviorScoringService scoringService,
      CrmQuoteProperties quoteProperties,
      QuoteDiscountApprovalSupport discountApprovalSupport) {
    this.quotationRepository = quotationRepository;
    this.opportunityRepository = opportunityRepository;
    this.leadRepository = leadRepository;
    this.timelineService = timelineService;
    this.notificationClient = notificationClient;
    this.orderClient = orderClient;
    this.paymentLinkProvider = paymentLinkProvider;
    this.scoringService = scoringService;
    this.quoteProperties = quoteProperties;
    this.discountApprovalSupport = discountApprovalSupport;
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
    quote.setApprovalStatus("NONE");
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
  public QuotationResponse markSent(Long id, QuotationSendRequest request) {
    String tenantId = TenantIds.require();
    CrmQuotationEntity quote = require(tenantId, id);
    if ("SUPERSEDED".equalsIgnoreCase(quote.getStatus())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot send a superseded quote version");
    }
    enforceDiscountApprovalGate(quote);
    Map<String, Object> share = new LinkedHashMap<>(buildSharePayload(quote));

    String channel =
        request != null && request.channel() != null && !request.channel().isBlank()
            ? request.channel().trim().toUpperCase(Locale.ROOT)
            : null;
    String recipient =
        request != null && request.recipient() != null && !request.recipient().isBlank()
            ? request.recipient().trim()
            : null;

    if (channel != null) {
      if (!SEND_CHANNELS.contains(channel)) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "channel must be EMAIL, WHATSAPP, or SMS");
      }
      if (recipient == null) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "recipient is required when channel is set");
      }
      String subject = String.valueOf(share.getOrDefault("emailSubject", "Quotation " + quote.getQuoteNumber()));
      String body =
          "EMAIL".equals(channel)
              ? String.valueOf(share.getOrDefault("emailBody", share.get("whatsappText")))
              : String.valueOf(share.getOrDefault("whatsappText", ""));
      String templateCode = quoteTemplateCode(channel);
      Map<String, String> variables = quoteTemplateVariables(quote, share);
      Map<String, Object> delivery =
          notificationClient.queue(
              tenantId,
              channel,
              recipient,
              subject,
              body,
              "crm-quote-" + quote.getId() + "-" + quote.getVersionNo() + "-" + channel,
              templateCode,
              variables);
      share.put("lastDelivery", delivery);
      share.put("note", "Queued via notification-service template " + templateCode + " (or skipped/fail-open)");
    } else {
      share.put("note", "Marked SENT without outbound channel — pass channel+recipient to dispatch");
    }

    quote.setStatus("SENT");
    quote.setSharePayloadJson(share);
    quote.touch();
    quote = quotationRepository.save(quote);
    final Long quotationId = quote.getId();
    final Long opportunityId = quote.getOpportunityId();
    final String quoteNumber = quote.getQuoteNumber();
    timelineService.recordEvent(
        "OPPORTUNITY",
        opportunityId,
        "QUOTE_SENT",
        "Quote " + quoteNumber + " marked SENT"
            + (channel != null ? " via " + channel : ""),
        Map.of(
            "quotationId",
            quotationId,
            "channel",
            channel == null ? "" : channel,
            "recipient",
            recipient == null ? "" : recipient));
    opportunityRepository
        .findByTenantIdAndIdAndDeletedAtIsNull(tenantId, opportunityId)
        .map(CrmOpportunityEntity::getLeadId)
        .filter(Objects::nonNull)
        .ifPresent(
            leadId ->
                scoringService.applyEvent(
                    leadId,
                    "QUOTE_SENT",
                    "Quote " + quoteNumber + " sent",
                    Map.of("quotationId", quotationId)));
    return toResponse(quote);
  }

  @Transactional
  public QuotationResponse accept(Long id) {
    String tenantId = TenantIds.require();
    CrmQuotationEntity quote = require(tenantId, id);
    String status = quote.getStatus() == null ? "" : quote.getStatus().trim().toUpperCase();
    if ("SUPERSEDED".equals(status)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot accept a superseded quote");
    }
    if ("ACCEPTED".equals(status)) {
      // Idempotent: re-run order create path if still missing, else return current
      Map<String, Object> share =
          new LinkedHashMap<>(
              quote.getSharePayloadJson() == null ? buildSharePayload(quote) : quote.getSharePayloadJson());
      if (share.get("orderId") == null && orderClient.isEnabled()) {
        Map<String, Object> orderResult = maybeCreateOrder(tenantId, quote, share);
        if (orderResult != null) {
          share.put("orderCreate", orderResult);
          if (orderResult.get("orderId") != null) {
            share.put("orderId", orderResult.get("orderId"));
            share.put("orderNumber", orderResult.get("orderNumber"));
          }
          quote.setSharePayloadJson(share);
          quote.touch();
          quote = quotationRepository.save(quote);
        }
      }
      return toResponse(quote);
    }
    quote.setStatus("ACCEPTED");
    quote.setAcceptedAt(java.time.Instant.now());
    Map<String, Object> share =
        new LinkedHashMap<>(
            quote.getSharePayloadJson() == null ? buildSharePayload(quote) : quote.getSharePayloadJson());
    Map<String, Object> orderResult = maybeCreateOrder(tenantId, quote, share);
    if (orderResult != null) {
      share.put("orderCreate", orderResult);
      if (orderResult.get("orderId") != null) {
        share.put("orderId", orderResult.get("orderId"));
        share.put("orderNumber", orderResult.get("orderNumber"));
      }
    }
    quote.setSharePayloadJson(share);
    quote.touch();
    quote = quotationRepository.save(quote);
    timelineService.recordEvent(
        "OPPORTUNITY",
        quote.getOpportunityId(),
        "QUOTE_ACCEPTED",
        "Quote " + quote.getQuoteNumber() + " accepted"
            + (orderResult != null && orderResult.get("orderId") != null
                ? " · order " + orderResult.get("orderId")
                : ""),
        Map.of(
            "quotationId",
            quote.getId(),
            "orderStatus",
            orderResult == null ? "SKIPPED" : String.valueOf(orderResult.get("status"))));
    return toResponse(quote);
  }

  /**
   * Clone quote into a new DRAFT version (same quote number). Marks the source SUPERSEDED.
   */
  @Transactional
  public QuotationResponse revise(Long id) {
    String tenantId = TenantIds.require();
    CrmQuotationEntity src = require(tenantId, id);
    if ("SUPERSEDED".equalsIgnoreCase(src.getStatus())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot revise a superseded quote");
    }
    int nextVersion =
        quotationRepository
                .findByTenantIdAndQuoteNumberAndDeletedAtIsNullOrderByVersionNoDesc(
                    tenantId, src.getQuoteNumber())
                .stream()
                .mapToInt(CrmQuotationEntity::getVersionNo)
                .max()
                .orElse(src.getVersionNo())
            + 1;

    src.setStatus("SUPERSEDED");
    src.touch();
    quotationRepository.save(src);

    CrmQuotationEntity copy = new CrmQuotationEntity();
    copy.setTenantId(tenantId);
    copy.setOpportunityId(src.getOpportunityId());
    copy.setQuoteNumber(src.getQuoteNumber());
    copy.setVersionNo(nextVersion);
    copy.setParentQuotationId(src.getId());
    copy.setStatus("DRAFT");
    copy.setApprovalStatus("NONE");
    copy.setApprovalId(null);
    copy.setCustomerName(src.getCustomerName());
    copy.setCustomerGstin(src.getCustomerGstin());
    copy.setPlaceOfSupply(src.getPlaceOfSupply());
    copy.setSellerStateCode(src.getSellerStateCode());
    copy.setBuyerStateCode(src.getBuyerStateCode());
    copy.setCurrency(src.getCurrency());
    copy.setTaxableAmount(src.getTaxableAmount());
    copy.setCgstAmount(src.getCgstAmount());
    copy.setSgstAmount(src.getSgstAmount());
    copy.setIgstAmount(src.getIgstAmount());
    copy.setTotalAmount(src.getTotalAmount());
    copy.setDiscountAmount(src.getDiscountAmount());
    copy.setTerms(src.getTerms());
    copy.setValidUntil(src.getValidUntil());
    copy.setLinesJson(new ArrayList<>(src.getLinesJson() == null ? List.of() : src.getLinesJson()));
    copy.setSharePayloadJson(buildSharePayload(copy));
    copy.setPaymentStatus("NONE");
    copy = quotationRepository.save(copy);

    timelineService.recordEvent(
        "OPPORTUNITY",
        copy.getOpportunityId(),
        "QUOTE_REVISED",
        "Quote "
            + copy.getQuoteNumber()
            + " v"
            + copy.getVersionNo()
            + " drafted from v"
            + src.getVersionNo(),
        Map.of(
            "quotationId",
            copy.getId(),
            "parentQuotationId",
            src.getId(),
            "versionNo",
            copy.getVersionNo()));
    return toResponse(copy);
  }

  /** Explicitly open a discount approval (also done automatically on send when gated). */
  @Transactional
  public QuotationResponse requestDiscountApproval(Long id) {
    String tenantId = TenantIds.require();
    CrmQuotationEntity quote = require(tenantId, id);
    if (!requiresDiscountApproval(quote)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Discount is below threshold ("
              + quoteProperties.getDiscountApprovalThresholdPercent()
              + "%); approval not required");
    }
    if ("APPROVED".equalsIgnoreCase(quote.getApprovalStatus())) {
      return toResponse(quote);
    }
    discountApprovalSupport.ensurePendingApproval(quote.getId());
    return toResponse(require(tenantId, id));
  }

  @Transactional
  public void applyDiscountApprovalDecision(Long quotationId, boolean approved, Long approvalId) {
    String tenantId = TenantIds.require();
    quotationRepository
        .findByTenantIdAndIdAndDeletedAtIsNull(tenantId, quotationId)
        .ifPresent(
            quote -> {
              quote.setApprovalStatus(approved ? "APPROVED" : "REJECTED");
              quote.setApprovalId(approvalId);
              quote.touch();
              quotationRepository.save(quote);
              timelineService.recordEvent(
                  "OPPORTUNITY",
                  quote.getOpportunityId(),
                  approved ? "QUOTE_DISCOUNT_APPROVED" : "QUOTE_DISCOUNT_REJECTED",
                  "Discount "
                      + (approved ? "approved" : "rejected")
                      + " for "
                      + quote.getQuoteNumber()
                      + " v"
                      + quote.getVersionNo(),
                  Map.of("quotationId", quote.getId(), "approvalId", approvalId));
            });
  }

  private void enforceDiscountApprovalGate(CrmQuotationEntity quote) {
    if (!requiresDiscountApproval(quote)) {
      return;
    }
    if ("APPROVED".equalsIgnoreCase(quote.getApprovalStatus())) {
      return;
    }
    Long approvalId = discountApprovalSupport.ensurePendingApproval(quote.getId());
    throw new ResponseStatusException(
        HttpStatus.CONFLICT,
        "Discount approval required before send (approvalId="
            + approvalId
            + ", threshold="
            + quoteProperties.getDiscountApprovalThresholdPercent()
            + "%)");
  }

  private boolean requiresDiscountApproval(CrmQuotationEntity quote) {
    if (!quoteProperties.isDiscountApprovalEnabled()) {
      return false;
    }
    BigDecimal threshold = quoteProperties.getDiscountApprovalThresholdPercent();
    if (threshold == null || threshold.compareTo(BigDecimal.ZERO) <= 0) {
      return false;
    }
    return discountPercent(quote).compareTo(threshold) > 0;
  }

  private static BigDecimal discountPercent(CrmQuotationEntity quote) {
    BigDecimal discount = nvl(quote.getDiscountAmount());
    if (discount.compareTo(BigDecimal.ZERO) <= 0) {
      return BigDecimal.ZERO;
    }
    BigDecimal base = nvl(quote.getTaxableAmount()).add(discount);
    if (base.compareTo(BigDecimal.ZERO) <= 0) {
      return BigDecimal.ZERO;
    }
    return discount
        .multiply(BigDecimal.valueOf(100))
        .divide(base, 2, RoundingMode.HALF_UP);
  }

  private String quoteTemplateCode(String channel) {
    var props = notificationClient.properties();
    return switch (channel) {
      case "EMAIL" -> props.getQuoteEmailTemplate();
      case "SMS" -> props.getQuoteSmsTemplate();
      default -> props.getQuoteWhatsappTemplate();
    };
  }

  private static Map<String, String> quoteTemplateVariables(
      CrmQuotationEntity quote, Map<String, Object> share) {
    Map<String, String> vars = new LinkedHashMap<>();
    vars.put("customerName", Objects.toString(quote.getCustomerName(), "Customer"));
    vars.put("quoteNumber", Objects.toString(quote.getQuoteNumber(), ""));
    vars.put("totalAmount", Objects.toString(quote.getTotalAmount(), "0"));
    Object pay = share.get("paymentLink");
    if (pay == null) {
      pay = quote.getPaymentLinkUrl();
    }
    vars.put("paymentLink", pay == null ? "" : String.valueOf(pay));
    return vars;
  }

  private Map<String, Object> maybeCreateOrder(
      String tenantId, CrmQuotationEntity quote, Map<String, Object> share) {
    if (!orderClient.isEnabled()) {
      return Map.of("status", "SKIPPED_DISABLED");
    }
    if (share.get("orderId") != null) {
      return Map.of("status", "ACKED", "orderId", share.get("orderId"), "alreadyCreated", true);
    }
    Long customerId = resolveCustomerId(tenantId, quote);
    Long productId = orderClient.properties().getDefaultProductId();
    List<Map<String, Object>> items = toOrderItems(quote, productId);
    boolean anyProduct =
        items.stream().anyMatch(i -> i.get("productId") != null);
    if (customerId == null || !anyProduct) {
      Map<String, Object> skipped = new LinkedHashMap<>();
      skipped.put("status", "SKIPPED_UNMAPPED");
      skipped.put(
          "note",
          "Set crm.order.default-product-id (or line productId) and convert lead to SHOP_CUSTOMER / set default-customer-id");
      return skipped;
    }
    if (items.isEmpty()) {
      return Map.of("status", "SKIPPED_NO_LINES");
    }
    Map<String, Object> totals = new LinkedHashMap<>();
    totals.put("subtotalAmount", toDouble(quote.getTaxableAmount()));
    totals.put("taxAmount", toDouble(nvl(quote.getCgstAmount()).add(nvl(quote.getSgstAmount())).add(nvl(quote.getIgstAmount()))));
    totals.put("cgstAmount", toDouble(quote.getCgstAmount()));
    totals.put("sgstAmount", toDouble(quote.getSgstAmount()));
    totals.put("igstAmount", toDouble(quote.getIgstAmount()));
    totals.put("totalAmount", toDouble(quote.getTotalAmount()));
    totals.put("discountAmount", toDouble(quote.getDiscountAmount()));
    totals.put("customerStateCode", quote.getBuyerStateCode());
    Map<String, Object> payload = orderClient.buildOrderPayload(customerId, productId, items, totals);
    return orderClient.createFromQuote(tenantId, "crm-quote-order-" + quote.getId(), payload);
  }

  private Long resolveCustomerId(String tenantId, CrmQuotationEntity quote) {
    Long fromConfig = orderClient.properties().getDefaultCustomerId();
    CrmOpportunityEntity opp =
        opportunityRepository
            .findByTenantIdAndIdAndDeletedAtIsNull(tenantId, quote.getOpportunityId())
            .orElse(null);
    if (opp == null || opp.getLeadId() == null) {
      return fromConfig;
    }
    return leadRepository
        .findByTenantIdAndIdAndDeletedAtIsNull(tenantId, opp.getLeadId())
        .map(CrmLeadEntity::getExternalRefs)
        .map(
            refs -> {
              Object entry = refs.get("SHOP_CUSTOMER");
              if (!(entry instanceof Map<?, ?> map)) {
                return null;
              }
              return asLong(map.get("externalId"));
            })
        .orElse(fromConfig);
  }

  private static List<Map<String, Object>> toOrderItems(CrmQuotationEntity quote, Long productId) {
    List<?> lines = quote.getLinesJson();
    if (lines == null || lines.isEmpty()) {
      return List.of();
    }
    List<Map<String, Object>> items = new ArrayList<>();
    for (Object row : lines) {
      if (!(row instanceof Map<?, ?> map)) {
        continue;
      }
      Map<String, Object> item = new LinkedHashMap<>();
      Long lineProduct = asLong(map.get("productId"));
      item.put("productId", lineProduct != null ? lineProduct : productId);
      item.put("productName", Objects.toString(map.get("description"), "CRM line"));
      item.put("hsnSac", map.get("hsn") == null ? null : String.valueOf(map.get("hsn")));
      BigDecimal qty = asDecimal(map.get("qty"));
      item.put("quantity", qty == null ? 1 : Math.max(1, qty.intValue()));
      item.put("price", toDouble(asDecimal(map.get("unitPrice"))));
      item.put("gstPercent", toDouble(asDecimal(map.get("gstRate"))));
      item.put("discountAmount", toDouble(asDecimal(map.get("discount"))));
      items.add(item);
    }
    return items;
  }

  private static Long asLong(Object v) {
    if (v == null) {
      return null;
    }
    if (v instanceof Number n) {
      return n.longValue();
    }
    try {
      return Long.parseLong(String.valueOf(v).trim());
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private static BigDecimal asDecimal(Object v) {
    if (v == null) {
      return null;
    }
    if (v instanceof BigDecimal bd) {
      return bd;
    }
    if (v instanceof Number n) {
      return BigDecimal.valueOf(n.doubleValue());
    }
    try {
      return new BigDecimal(String.valueOf(v).trim());
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private static Double toDouble(BigDecimal v) {
    return v == null ? null : v.doubleValue();
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
      m.put("productId", line.productId());
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

  @Transactional
  public QuotationResponse createPaymentLink(Long id) {
    String tenantId = TenantIds.require();
    CrmQuotationEntity quote = require(tenantId, id);
    PaymentLinkResult link = paymentLinkProvider.createPaymentLink(quote, tenantId);
    quote.setPaymentLinkUrl(link.url());
    quote.setPaymentStatus("LINK_CREATED");
    quote.setPaymentProvider(link.provider());
    quote.setPaymentRef(link.ref());
    quote.setPaymentAmount(link.amount() != null ? link.amount() : quote.getTotalAmount());
    Map<String, Object> share = new LinkedHashMap<>(buildSharePayload(quote));
    share.put("paymentLink", link.url());
    share.put("paymentRef", link.ref());
    String wa = String.valueOf(share.getOrDefault("whatsappText", ""));
    share.put("whatsappText", wa + "\nPay online: " + link.url());
    share.put(
        "emailBody", String.valueOf(share.getOrDefault("emailBody", "")) + "\n\nPay online: " + link.url());
    quote.setSharePayloadJson(share);
    quote.touch();
    quote = quotationRepository.save(quote);
    timelineService.recordEvent(
        "OPPORTUNITY",
        quote.getOpportunityId(),
        "PAYMENT_LINK_CREATED",
        "Payment link created for " + quote.getQuoteNumber() + " · " + link.provider(),
        Map.of("quotationId", quote.getId(), "paymentRef", link.ref(), "provider", link.provider()));
    return toResponse(quote);
  }

  @Transactional
  public QuotationResponse markPaid(Long id) {
    CrmQuotationEntity quote = require(TenantIds.require(), id);
    quote.setPaymentStatus("PAID");
    quote.setPaidAt(java.time.Instant.now());
    if (quote.getPaymentAmount() == null) {
      quote.setPaymentAmount(quote.getTotalAmount());
    }
    quote.touch();
    quote = quotationRepository.save(quote);
    timelineService.recordEvent(
        "OPPORTUNITY",
        quote.getOpportunityId(),
        "PAYMENT_RECEIVED",
        "Payment marked PAID for " + quote.getQuoteNumber(),
        Map.of("quotationId", quote.getId()));
    return toResponse(quote);
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
    if (quote.getPaymentLinkUrl() != null && !quote.getPaymentLinkUrl().isBlank()) {
      text = text + "\nPay online: " + quote.getPaymentLinkUrl();
      payload.put("paymentLink", quote.getPaymentLinkUrl());
    }
    payload.put("whatsappText", text);
    payload.put("emailSubject", "Quotation " + quote.getQuoteNumber());
    payload.put("emailBody", text);
    payload.put("channelHints", List.of("WHATSAPP", "EMAIL", "SMS"));
    payload.put(
        "note",
        "Pass channel+recipient on POST /quotations/{id}/send to queue via notification-service");
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
        q.getParentQuotationId(),
        q.getStatus(),
        q.getApprovalStatus(),
        q.getApprovalId(),
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
        q.getCreatedAt(),
        q.getPaymentLinkUrl(),
        q.getPaymentStatus(),
        q.getPaymentProvider(),
        q.getPaymentRef(),
        q.getPaymentAmount(),
        q.getPaidAt());
  }
}
