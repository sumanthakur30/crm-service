package com.shopmanagement.crmservice.web;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.shopmanagement.crmservice.api.CrmDealApi.QuotationResponse;
import com.shopmanagement.crmservice.api.CrmDealApi.QuotationSendRequest;
import com.shopmanagement.crmservice.api.CrmDealApi.QuotationUpsert;
import com.shopmanagement.crmservice.entitlement.CrmEntitlementGuard;
import com.shopmanagement.crmservice.service.QuotationPdfService;
import com.shopmanagement.crmservice.service.QuotationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/crm/quotations")
public class QuotationController {

  private final QuotationService quotationService;
  private final QuotationPdfService quotationPdfService;
  private final CrmEntitlementGuard entitlementGuard;

  public QuotationController(
      QuotationService quotationService,
      QuotationPdfService quotationPdfService,
      CrmEntitlementGuard entitlementGuard) {
    this.quotationService = quotationService;
    this.quotationPdfService = quotationPdfService;
    this.entitlementGuard = entitlementGuard;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public QuotationResponse create(@Valid @RequestBody QuotationUpsert body) {
    entitlementGuard.requireQuoteAccess();
    return quotationService.create(body);
  }

  @GetMapping("/{id}")
  public QuotationResponse get(@PathVariable Long id) {
    entitlementGuard.requireQuoteAccess();
    return quotationService.get(id);
  }

  @GetMapping("/by-opportunity/{opportunityId}")
  public List<QuotationResponse> byOpportunity(@PathVariable Long opportunityId) {
    entitlementGuard.requireQuoteAccess();
    return quotationService.listForOpportunity(opportunityId);
  }

  @GetMapping("/{id}/pdf")
  public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
    entitlementGuard.requireQuoteAccess();
    byte[] bytes = quotationPdfService.renderPdf(id);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"quotation-" + id + ".pdf\"")
        .contentType(MediaType.APPLICATION_PDF)
        .body(bytes);
  }

  @PostMapping("/{id}/payment-link")
  public QuotationResponse paymentLink(@PathVariable Long id) {
    entitlementGuard.requireQuoteAccess();
    return quotationService.createPaymentLink(id);
  }

  @PostMapping("/{id}/mark-paid")
  public QuotationResponse markPaid(@PathVariable Long id) {
    entitlementGuard.requireQuoteAccess();
    return quotationService.markPaid(id);
  }

  @PostMapping("/{id}/send")
  public QuotationResponse send(
      @PathVariable Long id, @RequestBody(required = false) @Valid QuotationSendRequest body) {
    entitlementGuard.requireQuoteAccess();
    if (body != null) {
      entitlementGuard.requireChannelAccess(body.channel());
    }
    return quotationService.markSent(id, body);
  }

  @PostMapping("/{id}/revise")
  @ResponseStatus(HttpStatus.CREATED)
  public QuotationResponse revise(@PathVariable Long id) {
    entitlementGuard.requireQuoteAccess();
    return quotationService.revise(id);
  }

  @PostMapping("/{id}/request-discount-approval")
  public QuotationResponse requestDiscountApproval(@PathVariable Long id) {
    entitlementGuard.requireQuoteAccess();
    entitlementGuard.requireApprovalAccess();
    return quotationService.requestDiscountApproval(id);
  }

  @PostMapping("/{id}/accept")
  public QuotationResponse accept(@PathVariable Long id) {
    entitlementGuard.requireQuoteAccess();
    return quotationService.accept(id);
  }
}
