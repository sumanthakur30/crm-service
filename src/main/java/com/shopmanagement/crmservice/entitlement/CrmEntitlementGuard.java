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
    if (!client.hasFeature(tenantId, properties.getFlag())) {
      throw new CrmEntitlementException(properties.getFlag() + " is not enabled for this tenant");
    }
  }

  /** Quote APIs — requires FEATURE_CRM plus FEATURE_CRM_QUOTE when quoteFlag is configured. */
  public void requireQuoteAccess() {
    requireCrmAccess();
    if (!properties.isEnabled()) {
      return;
    }
    String quoteFlag = properties.getQuoteFlag();
    if (quoteFlag == null || quoteFlag.isBlank()) {
      return;
    }
    String tenantId = TenantIds.require();
    if (!client.hasFeature(tenantId, quoteFlag.trim())) {
      throw new CrmEntitlementException(quoteFlag + " is not enabled — assign crm-professional or higher");
    }
  }
}
