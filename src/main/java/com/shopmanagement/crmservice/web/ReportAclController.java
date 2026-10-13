package com.shopmanagement.crmservice.web;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shopmanagement.crmservice.entitlement.CrmEntitlementGuard;
import com.shopmanagement.crmservice.service.ReportAclService;

@RestController
@RequestMapping("/api/v1/crm")
public class ReportAclController {

  private final ReportAclService reportAclService;
  private final CrmEntitlementGuard entitlementGuard;

  public ReportAclController(ReportAclService reportAclService, CrmEntitlementGuard entitlementGuard) {
    this.reportAclService = reportAclService;
    this.entitlementGuard = entitlementGuard;
  }

  @GetMapping("/reports/schedules")
  public List<Map<String, Object>> schedules() {
    entitlementGuard.requireCrmAccess();
    return reportAclService.listSchedules();
  }

  @PostMapping("/reports/schedules")
  public Map<String, Object> upsertSchedule(@RequestBody Map<String, Object> body) {
    entitlementGuard.requireCrmAccess();
    return reportAclService.upsertSchedule(body);
  }

  @PostMapping("/reports/run-due")
  public Map<String, Object> runDue() {
    entitlementGuard.requireCrmAccess();
    return reportAclService.runDueReports();
  }

  @GetMapping("/reports/schedules/{code}/last-result")
  public Map<String, Object> lastResult(@PathVariable String code) {
    entitlementGuard.requireCrmAccess();
    return reportAclService.lastResult(code);
  }

  @GetMapping("/acl/fields")
  public List<Map<String, Object>> listAcl(@RequestParam(required = false) String roleCode) {
    entitlementGuard.requireCrmAccess();
    return reportAclService.listAcl(roleCode);
  }

  @PostMapping("/acl/fields")
  public Map<String, Object> upsertAcl(@RequestBody Map<String, Object> body) {
    entitlementGuard.requireCrmAccess();
    return reportAclService.upsertFieldAcl(body);
  }

  @PostMapping("/acl/fields/ensure-defaults")
  public List<Map<String, Object>> ensureAcl() {
    entitlementGuard.requireCrmAccess();
    return reportAclService.ensureDefaultAcl();
  }

  @PostMapping("/acl/project-lead")
  public Map<String, Object> projectLead(@RequestBody Map<String, Object> body) {
    entitlementGuard.requireCrmAccess();
    String role = body.get("roleCode") == null ? "SALES_REP" : String.valueOf(body.get("roleCode"));
    @SuppressWarnings("unchecked")
    Map<String, Object> lead =
        body.get("lead") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    return reportAclService.projectLeadFields(role, lead);
  }
}
