package com.shopmanagement.crmservice.web;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shopmanagement.crmservice.entitlement.CrmEntitlementGuard;

@RestController
@RequestMapping("/api/v1/crm")
public class EntitlementController {

  private final CrmEntitlementGuard entitlementGuard;

  public EntitlementController(CrmEntitlementGuard entitlementGuard) {
    this.entitlementGuard = entitlementGuard;
  }

  /**
   * Feature snapshot for crm-ui tab gating. Does not require FEATURE_CRM itself so the UI can show
   * an upgrade message when the master flag is off.
   */
  @GetMapping("/entitlements")
  public Map<String, Object> entitlements() {
    return entitlementGuard.entitlementsSnapshot();
  }
}
