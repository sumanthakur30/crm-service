package com.shopmanagement.crmservice.web;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shopmanagement.crmservice.entitlement.CrmEntitlementGuard;
import com.shopmanagement.crmservice.service.LeadMergeService;

@RestController
@RequestMapping("/api/v1/crm/leads")
public class LeadMergeController {

  private final LeadMergeService leadMergeService;
  private final CrmEntitlementGuard entitlementGuard;

  public LeadMergeController(LeadMergeService leadMergeService, CrmEntitlementGuard entitlementGuard) {
    this.leadMergeService = leadMergeService;
    this.entitlementGuard = entitlementGuard;
  }

  @GetMapping("/{id}/duplicates")
  public List<Map<String, Object>> duplicates(@PathVariable Long id) {
    entitlementGuard.requireCrmAccess();
    return leadMergeService.findDuplicates(id);
  }

  @PostMapping("/{id}/merge/{duplicateId}")
  public Map<String, Object> merge(@PathVariable Long id, @PathVariable Long duplicateId) {
    entitlementGuard.requireCrmAccess();
    return leadMergeService.merge(id, duplicateId);
  }
}
