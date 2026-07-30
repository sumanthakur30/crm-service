package com.shopmanagement.crmservice.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shopmanagement.crmservice.config.CrmFieldForceProperties;
import com.shopmanagement.crmservice.entitlement.CrmEntitlementGuard;

@RestController
@RequestMapping("/api/v1/crm/field-force")
public class FieldForceEmbedController {

  private final CrmFieldForceProperties properties;
  private final CrmEntitlementGuard entitlementGuard;

  public FieldForceEmbedController(
      CrmFieldForceProperties properties, CrmEntitlementGuard entitlementGuard) {
    this.properties = properties;
    this.entitlementGuard = entitlementGuard;
  }

  @GetMapping("/embed-config")
  public Map<String, Object> embedConfig() {
    entitlementGuard.requireCrmAccess();
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("enabled", properties.isEmbedEnabled());
    out.put(
        "visitUrlTemplate",
        properties.getVisitUrlTemplate() == null ? "" : properties.getVisitUrlTemplate());
    out.put("openInNewTab", properties.isOpenInNewTab());
    return out;
  }
}
