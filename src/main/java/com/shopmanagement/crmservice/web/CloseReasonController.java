package com.shopmanagement.crmservice.web;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shopmanagement.crmservice.entitlement.CrmEntitlementGuard;
import com.shopmanagement.crmservice.service.CloseReasonService;

@RestController
@RequestMapping("/api/v1/crm")
public class CloseReasonController {

  private final CloseReasonService closeReasonService;
  private final CrmEntitlementGuard entitlementGuard;

  public CloseReasonController(
      CloseReasonService closeReasonService, CrmEntitlementGuard entitlementGuard) {
    this.closeReasonService = closeReasonService;
    this.entitlementGuard = entitlementGuard;
  }

  @GetMapping("/close-reasons")
  public List<Map<String, Object>> list(@RequestParam(required = false) String outcome) {
    entitlementGuard.requireCrmAccess();
    return closeReasonService.list(outcome);
  }
}
