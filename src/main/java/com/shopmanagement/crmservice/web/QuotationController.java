package com.shopmanagement.crmservice.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.shopmanagement.crmservice.api.CrmDealApi.QuotationResponse;
import com.shopmanagement.crmservice.api.CrmDealApi.QuotationUpsert;
import com.shopmanagement.crmservice.entitlement.CrmEntitlementGuard;
import com.shopmanagement.crmservice.service.QuotationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/crm/quotations")
public class QuotationController {

  private final QuotationService quotationService;
  private final CrmEntitlementGuard entitlementGuard;

  public QuotationController(QuotationService quotationService, CrmEntitlementGuard entitlementGuard) {
    this.quotationService = quotationService;
    this.entitlementGuard = entitlementGuard;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public QuotationResponse create(@Valid @RequestBody QuotationUpsert body) {
    entitlementGuard.requireCrmAccess();
    return quotationService.create(body);
  }

  @GetMapping("/{id}")
  public QuotationResponse get(@PathVariable Long id) {
    entitlementGuard.requireCrmAccess();
    return quotationService.get(id);
  }

  @GetMapping("/by-opportunity/{opportunityId}")
  public List<QuotationResponse> byOpportunity(@PathVariable Long opportunityId) {
    entitlementGuard.requireCrmAccess();
    return quotationService.listForOpportunity(opportunityId);
  }

  @PostMapping("/{id}/send")
  public QuotationResponse send(@PathVariable Long id) {
    entitlementGuard.requireCrmAccess();
    return quotationService.markSent(id);
  }

  @PostMapping("/{id}/accept")
  public QuotationResponse accept(@PathVariable Long id) {
    entitlementGuard.requireCrmAccess();
    return quotationService.accept(id);
  }
}
