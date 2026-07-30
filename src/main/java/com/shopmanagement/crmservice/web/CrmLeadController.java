package com.shopmanagement.crmservice.web;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.shopmanagement.crmservice.api.CrmLeadApi.LeadResponse;
import com.shopmanagement.crmservice.api.CrmLeadApi.LeadStatusPatch;
import com.shopmanagement.crmservice.api.CrmLeadApi.LeadUpsert;
import com.shopmanagement.crmservice.entitlement.CrmEntitlementGuard;
import com.shopmanagement.crmservice.service.CrmLeadService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/crm/leads")
public class CrmLeadController {

  private final CrmLeadService leadService;
  private final CrmEntitlementGuard entitlementGuard;

  public CrmLeadController(CrmLeadService leadService, CrmEntitlementGuard entitlementGuard) {
    this.leadService = leadService;
    this.entitlementGuard = entitlementGuard;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public LeadResponse create(@Valid @RequestBody LeadUpsert body) {
    entitlementGuard.requireCrmAccess();
    return leadService.create(body);
  }

  @PutMapping("/{id}")
  public LeadResponse update(@PathVariable Long id, @Valid @RequestBody LeadUpsert body) {
    entitlementGuard.requireCrmAccess();
    return leadService.update(id, body);
  }

  @PatchMapping("/{id}/status")
  public LeadResponse patchStatus(@PathVariable Long id, @Valid @RequestBody LeadStatusPatch body) {
    entitlementGuard.requireCrmAccess();
    return leadService.patchStatus(id, body);
  }

  @GetMapping("/{id}")
  public LeadResponse get(@PathVariable Long id) {
    entitlementGuard.requireCrmAccess();
    return leadService.get(id);
  }

  @GetMapping
  public Page<LeadResponse> list(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) Long stageId,
      @RequestParam(required = false) Long pipelineId,
      @RequestParam(required = false) String ownerUserId,
      @RequestParam(required = false) String q,
      Pageable pageable) {
    entitlementGuard.requireCrmAccess();
    return leadService.search(status, stageId, pipelineId, ownerUserId, q, pageable);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id) {
    entitlementGuard.requireCrmAccess();
    leadService.delete(id);
  }
}
