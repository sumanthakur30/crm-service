package com.shopmanagement.crmservice.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.shopmanagement.crmservice.config.CrmQuoteProperties;
import com.shopmanagement.crmservice.persistence.entity.CrmApprovalEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmQuotationEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmApprovalRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmQuotationRepository;
import com.shopmanagement.crmservice.support.TenantIds;

/**
 * Commits discount-approval rows in a nested transaction so a 409 from send does not roll them back.
 */
@Service
public class QuoteDiscountApprovalSupport {

  private final CrmQuotationRepository quotationRepository;
  private final CrmApprovalRepository approvalRepository;
  private final TimelineService timelineService;
  private final CrmQuoteProperties quoteProperties;

  public QuoteDiscountApprovalSupport(
      CrmQuotationRepository quotationRepository,
      CrmApprovalRepository approvalRepository,
      TimelineService timelineService,
      CrmQuoteProperties quoteProperties) {
    this.quotationRepository = quotationRepository;
    this.approvalRepository = approvalRepository;
    this.timelineService = timelineService;
    this.quoteProperties = quoteProperties;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Long ensurePendingApproval(Long quotationId) {
    String tenantId = TenantIds.require();
    CrmQuotationEntity quote =
        quotationRepository
            .findByTenantIdAndIdAndDeletedAtIsNull(tenantId, quotationId)
            .orElseThrow();
    if ("PENDING".equalsIgnoreCase(quote.getApprovalStatus()) && quote.getApprovalId() != null) {
      return quote.getApprovalId();
    }
    return approvalRepository
        .findFirstByTenantIdAndObjectTypeAndObjectIdAndStatusOrderByCreatedAtDesc(
            tenantId, "QUOTATION", quote.getId(), "PENDING")
        .map(
            existing -> {
              quote.setApprovalStatus("PENDING");
              quote.setApprovalId(existing.getId());
              quote.touch();
              quotationRepository.save(quote);
              return existing.getId();
            })
        .orElseGet(
            () -> {
              CrmApprovalEntity a = new CrmApprovalEntity();
              a.setTenantId(tenantId);
              a.setObjectType("QUOTATION");
              a.setObjectId(quote.getId());
              a.setTitle(
                  "Discount approval · "
                      + quote.getQuoteNumber()
                      + " v"
                      + quote.getVersionNo()
                      + " · "
                      + discountPercent(quote)
                      + "% (₹"
                      + nvl(quote.getDiscountAmount())
                      + ")");
              a.setStatus("PENDING");
              a.setRequestedBy(TenantIds.currentUserOrNull());
              Map<String, Object> attrs = new LinkedHashMap<>();
              attrs.put("discountAmount", quote.getDiscountAmount());
              attrs.put("discountPercent", discountPercent(quote));
              attrs.put("thresholdPercent", quoteProperties.getDiscountApprovalThresholdPercent());
              attrs.put("totalAmount", quote.getTotalAmount());
              a.setAttributes(attrs);
              a = approvalRepository.save(a);
              quote.setApprovalStatus("PENDING");
              quote.setApprovalId(a.getId());
              quote.touch();
              quotationRepository.save(quote);
              timelineService.recordEvent(
                  "OPPORTUNITY",
                  quote.getOpportunityId(),
                  "QUOTE_DISCOUNT_APPROVAL_REQUESTED",
                  a.getTitle(),
                  Map.of("quotationId", quote.getId(), "approvalId", a.getId()));
              return a.getId();
            });
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
    return discount.multiply(BigDecimal.valueOf(100)).divide(base, 2, RoundingMode.HALF_UP);
  }

  private static BigDecimal nvl(BigDecimal v) {
    return v == null ? BigDecimal.ZERO : v;
  }
}
