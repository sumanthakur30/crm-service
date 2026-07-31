package com.shopmanagement.crmservice.web;

import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shopmanagement.crmservice.entitlement.CrmEntitlementGuard;
import com.shopmanagement.crmservice.service.CtiService;

@RestController
@RequestMapping("/api/v1/crm/cti")
public class CtiController {

  private final CtiService ctiService;
  private final CrmEntitlementGuard entitlementGuard;

  public CtiController(CtiService ctiService, CrmEntitlementGuard entitlementGuard) {
    this.ctiService = ctiService;
    this.entitlementGuard = entitlementGuard;
  }

  @PostMapping("/click-to-dial")
  public Map<String, Object> clickToDial(@RequestBody Map<String, Object> body) {
    entitlementGuard.requireCrmAccess();
    return ctiService.clickToDial(body);
  }
}
