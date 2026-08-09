package com.shopmanagement.crmservice.web;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shopmanagement.crmservice.entitlement.CrmEntitlementGuard;
import com.shopmanagement.crmservice.service.AnalyticsService;

@RestController
@RequestMapping("/api/v1/crm/analytics")
public class AnalyticsController {

  private final AnalyticsService analyticsService;
  private final CrmEntitlementGuard entitlementGuard;

  public AnalyticsController(AnalyticsService analyticsService, CrmEntitlementGuard entitlementGuard) {
    this.analyticsService = analyticsService;
    this.entitlementGuard = entitlementGuard;
  }

  @GetMapping("/summary")
  public Map<String, Object> summary() {
    entitlementGuard.requireCrmAccess();
    return analyticsService.summary();
  }

  @GetMapping("/pipeline")
  public Map<String, Object> pipeline() {
    entitlementGuard.requireCrmAccess();
    return analyticsService.pipeline();
  }
}
