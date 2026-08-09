package com.shopmanagement.crmservice.web;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.shopmanagement.crmservice.entitlement.CrmEntitlementGuard;
import com.shopmanagement.crmservice.service.DuplicateRuleService;

@RestController
@RequestMapping("/api/v1/crm/duplicate-rules")
public class DuplicateRuleController {

  private final DuplicateRuleService duplicateRuleService;
  private final CrmEntitlementGuard entitlementGuard;

  public DuplicateRuleController(
      DuplicateRuleService duplicateRuleService, CrmEntitlementGuard entitlementGuard) {
    this.duplicateRuleService = duplicateRuleService;
    this.entitlementGuard = entitlementGuard;
  }

  @GetMapping
  public List<Map<String, Object>> list(@RequestParam(defaultValue = "LEAD") String objectType) {
    entitlementGuard.requireCrmAccess();
    return duplicateRuleService.listOrEnsure(objectType);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> upsert(@RequestBody Map<String, Object> body) {
    entitlementGuard.requireCrmAccess();
    return duplicateRuleService.upsert(body);
  }
}
