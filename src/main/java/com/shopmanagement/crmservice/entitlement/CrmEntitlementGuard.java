package com.shopmanagement.crmservice.entitlement;

import org.springframework.stereotype.Component;

import com.shopmanagement.crmservice.config.CrmProperties;
import com.shopmanagement.crmservice.support.TenantIds;

@Component
public class CrmEntitlementGuard {

  private final CrmProperties properties;
  private final SubscriptionEntitlementClient client;

  public CrmEntitlementGuard(CrmProperties properties, SubscriptionEntitlementClient client) {
    this.properties = properties;
    this.client = client;
  }

  public void requireCrmAccess() {
    if (!properties.isEnabled()) {
      return;
    }
    String tenantId = TenantIds.require();
    if (!client.hasCrmFeature(tenantId)) {
      throw new CrmEntitlementException("FEATURE_CRM is not enabled for this tenant");
    }
  }
}
