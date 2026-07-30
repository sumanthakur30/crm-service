package com.shopmanagement.crmservice.web;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shopmanagement.crmservice.entitlement.CrmEntitlementGuard;
import com.shopmanagement.crmservice.service.EnterpriseGovernanceService;

@RestController
@RequestMapping("/api/v1/crm/enterprise")
public class EnterpriseController {

  private final EnterpriseGovernanceService enterpriseService;
  private final CrmEntitlementGuard entitlementGuard;

  public EnterpriseController(
      EnterpriseGovernanceService enterpriseService, CrmEntitlementGuard entitlementGuard) {
    this.enterpriseService = enterpriseService;
    this.entitlementGuard = entitlementGuard;
  }

  @GetMapping("/settings")
  public Map<String, Object> settings() {
    entitlementGuard.requireCrmAccess();
    return enterpriseService.getOrCreateSettings();
  }

  @PutMapping("/settings")
  public Map<String, Object> updateSettings(@RequestBody Map<String, Object> body) {
    entitlementGuard.requireCrmAccess();
    return enterpriseService.updateSettings(body);
  }

  @GetMapping("/sso")
  public Map<String, Object> sso() {
    entitlementGuard.requireCrmAccess();
    return enterpriseService.ssoStatus();
  }

  @PostMapping("/audit-exports")
  public Map<String, Object> requestExport(@RequestBody(required = false) Map<String, Object> body) {
    entitlementGuard.requireCrmAccess();
    return enterpriseService.requestAuditExport(body == null ? Map.of() : body);
  }

  @GetMapping("/audit-exports")
  public List<Map<String, Object>> listExports() {
    entitlementGuard.requireCrmAccess();
    return enterpriseService.listAuditExports();
  }

  @GetMapping("/audit-exports/{id}")
  public Map<String, Object> getExport(@PathVariable Long id) {
    entitlementGuard.requireCrmAccess();
    return enterpriseService.getAuditExport(id);
  }
}
