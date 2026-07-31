package com.shopmanagement.crmservice.web;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shopmanagement.crmservice.entitlement.CrmEntitlementGuard;
import com.shopmanagement.crmservice.service.SsoHandshakeService;

@RestController
@RequestMapping("/api/v1/crm/sso")
public class SsoController {

  private final SsoHandshakeService ssoHandshakeService;
  private final CrmEntitlementGuard entitlementGuard;

  public SsoController(SsoHandshakeService ssoHandshakeService, CrmEntitlementGuard entitlementGuard) {
    this.ssoHandshakeService = ssoHandshakeService;
    this.entitlementGuard = entitlementGuard;
  }

  @GetMapping("/status")
  public Map<String, Object> status() {
    entitlementGuard.requireCrmAccess();
    return ssoHandshakeService.status();
  }

  @GetMapping("/authorize")
  public Map<String, Object> authorize() {
    entitlementGuard.requireCrmAccess();
    return ssoHandshakeService.authorize();
  }

  /** Browser redirect target — allowlisted in TenantContextFilter (no X-Tenant-Id). */
  @GetMapping("/callback")
  public Map<String, Object> callback(
      @RequestParam(required = false) String code, @RequestParam(required = false) String state) {
    return ssoHandshakeService.callback(code, state);
  }
}
