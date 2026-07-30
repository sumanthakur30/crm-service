package com.shopmanagement.crmservice.web;

import java.time.Instant;
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
import com.shopmanagement.crmservice.service.OpsService;

@RestController
@RequestMapping("/api/v1/crm/ops")
public class OpsController {

  private final OpsService opsService;
  private final CrmEntitlementGuard entitlementGuard;

  public OpsController(OpsService opsService, CrmEntitlementGuard entitlementGuard) {
    this.opsService = opsService;
    this.entitlementGuard = entitlementGuard;
  }

  @PostMapping("/calendar")
  public Map<String, Object> createCalendar(@RequestBody Map<String, Object> body) {
    entitlementGuard.requireCrmAccess();
    return opsService.createCalendarEvent(body);
  }

  @GetMapping("/calendar")
  public List<Map<String, Object>> listCalendar(@RequestParam(required = false) String from) {
    entitlementGuard.requireCrmAccess();
    Instant start = from == null || from.isBlank() ? null : Instant.parse(from);
    return opsService.listCalendar(start);
  }

  @PostMapping("/calls")
  public Map<String, Object> logCall(@RequestBody Map<String, Object> body) {
    entitlementGuard.requireCrmAccess();
    return opsService.logCall(body);
  }

  @GetMapping("/calls/by-lead/{leadId}")
  public List<Map<String, Object>> calls(@PathVariable Long leadId) {
    entitlementGuard.requireCrmAccess();
    return opsService.callsForLead(leadId);
  }

  @PostMapping("/approvals")
  public Map<String, Object> requestApproval(@RequestBody Map<String, Object> body) {
    entitlementGuard.requireApprovalAccess();
    return opsService.requestApproval(body);
  }

  @GetMapping("/approvals")
  public List<Map<String, Object>> approvals(@RequestParam(required = false) String status) {
    entitlementGuard.requireApprovalAccess();
    return opsService.listApprovals(status);
  }

  @PostMapping("/approvals/{id}/decide")
  public Map<String, Object> decide(
      @PathVariable Long id, @RequestParam boolean approve, @RequestParam(required = false) String note) {
    entitlementGuard.requireApprovalAccess();
    return opsService.decideApproval(id, approve, note);
  }

  @GetMapping("/forecast")
  public Map<String, Object> forecast() {
    entitlementGuard.requireCrmAccess();
    return opsService.forecast();
  }
}
