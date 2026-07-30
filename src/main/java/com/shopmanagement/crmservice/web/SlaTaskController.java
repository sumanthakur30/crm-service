package com.shopmanagement.crmservice.web;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shopmanagement.crmservice.entitlement.CrmEntitlementGuard;
import com.shopmanagement.crmservice.service.SlaTaskService;

@RestController
@RequestMapping("/api/v1/crm/tasks")
public class SlaTaskController {

  private final SlaTaskService slaTaskService;
  private final CrmEntitlementGuard entitlementGuard;

  public SlaTaskController(SlaTaskService slaTaskService, CrmEntitlementGuard entitlementGuard) {
    this.slaTaskService = slaTaskService;
    this.entitlementGuard = entitlementGuard;
  }

  @PostMapping("/sla/ensure-default")
  public Map<String, Object> ensureDefault() {
    entitlementGuard.requireCrmAccess();
    return slaTaskService.ensureDefaultLeadSla();
  }

  @PostMapping("/sla/process-aging")
  public Map<String, Object> processAging() {
    entitlementGuard.requireCrmAccess();
    return slaTaskService.processAging();
  }

  @GetMapping
  public List<Map<String, Object>> openTasks() {
    entitlementGuard.requireCrmAccess();
    return slaTaskService.listOpenTasks();
  }

  @PostMapping("/{id}/complete")
  public Map<String, Object> complete(@PathVariable Long id) {
    entitlementGuard.requireCrmAccess();
    return slaTaskService.completeTask(id);
  }
}
