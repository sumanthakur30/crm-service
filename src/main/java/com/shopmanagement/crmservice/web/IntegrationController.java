package com.shopmanagement.crmservice.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shopmanagement.crmservice.api.CrmIntegrationApi.IntegrationCard;
import com.shopmanagement.crmservice.entitlement.CrmEntitlementGuard;
import com.shopmanagement.crmservice.service.IntegrationQueryService;

@RestController
@RequestMapping("/api/v1/crm/integrations")
public class IntegrationController {

  private final IntegrationQueryService integrationQueryService;
  private final CrmEntitlementGuard entitlementGuard;

  public IntegrationController(
      IntegrationQueryService integrationQueryService, CrmEntitlementGuard entitlementGuard) {
    this.integrationQueryService = integrationQueryService;
    this.entitlementGuard = entitlementGuard;
  }

  @GetMapping
  public List<IntegrationCard> list() {
    entitlementGuard.requireCrmAccess();
    return integrationQueryService.list();
  }
}
