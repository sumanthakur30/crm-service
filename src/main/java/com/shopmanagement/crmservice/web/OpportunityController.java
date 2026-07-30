package com.shopmanagement.crmservice.web;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.shopmanagement.crmservice.api.CrmDealApi.OpportunityResponse;
import com.shopmanagement.crmservice.api.CrmDealApi.OpportunityUpsert;
import com.shopmanagement.crmservice.entitlement.CrmEntitlementGuard;
import com.shopmanagement.crmservice.service.OpportunityService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/crm/opportunities")
public class OpportunityController {

  private final OpportunityService opportunityService;
  private final CrmEntitlementGuard entitlementGuard;

  public OpportunityController(OpportunityService opportunityService, CrmEntitlementGuard entitlementGuard) {
    this.opportunityService = opportunityService;
    this.entitlementGuard = entitlementGuard;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public OpportunityResponse create(@Valid @RequestBody OpportunityUpsert body) {
    entitlementGuard.requireCrmAccess();
    return opportunityService.create(body);
  }

  @PutMapping("/{id}")
  public OpportunityResponse update(@PathVariable Long id, @Valid @RequestBody OpportunityUpsert body) {
    entitlementGuard.requireCrmAccess();
    return opportunityService.update(id, body);
  }

  @GetMapping("/{id}")
  public OpportunityResponse get(@PathVariable Long id) {
    entitlementGuard.requireCrmAccess();
    return opportunityService.get(id);
  }

  @GetMapping
  public Page<OpportunityResponse> list(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) Long stageId,
      @RequestParam(required = false) String q,
      Pageable pageable) {
    entitlementGuard.requireCrmAccess();
    return opportunityService.search(status, stageId, q, pageable);
  }

  @PostMapping("/{id}/stage/{stageId}")
  public OpportunityResponse moveStage(@PathVariable Long id, @PathVariable Long stageId) {
    entitlementGuard.requireCrmAccess();
    return opportunityService.moveStage(id, stageId);
  }
}
