package com.shopmanagement.crmservice.web;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.shopmanagement.crmservice.entitlement.CrmEntitlementGuard;
import com.shopmanagement.crmservice.service.StageAutomationService;

@RestController
@RequestMapping("/api/v1/crm/automation")
public class StageAutomationController {

  private final StageAutomationService stageAutomationService;
  private final CrmEntitlementGuard entitlementGuard;

  public StageAutomationController(
      StageAutomationService stageAutomationService, CrmEntitlementGuard entitlementGuard) {
    this.stageAutomationService = stageAutomationService;
    this.entitlementGuard = entitlementGuard;
  }

  @GetMapping("/stage-rules")
  public List<Map<String, Object>> list() {
    entitlementGuard.requireAutomationAccess();
    return stageAutomationService.listRules();
  }

  @PostMapping("/stage-rules")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> create(@RequestBody Map<String, Object> body) {
    entitlementGuard.requireAutomationAccess();
    return stageAutomationService.createRule(body);
  }

  @PostMapping("/stage-rules/{id}/activate")
  public Map<String, Object> activate(@org.springframework.web.bind.annotation.PathVariable Long id) {
    entitlementGuard.requireAutomationAccess();
    return stageAutomationService.setRuleActive(id, true);
  }

  @PostMapping("/stage-rules/{id}/deactivate")
  public Map<String, Object> deactivate(@org.springframework.web.bind.annotation.PathVariable Long id) {
    entitlementGuard.requireAutomationAccess();
    return stageAutomationService.setRuleActive(id, false);
  }
}
