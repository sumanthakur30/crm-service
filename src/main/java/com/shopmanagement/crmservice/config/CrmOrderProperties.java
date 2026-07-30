package com.shopmanagement.crmservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Quote ACCEPT → order-service create. Off by default; requires numeric shop/tenant and catalog
 * product/customer mapping for Retail stock reserve.
 */
@ConfigurationProperties(prefix = "crm.order")
public class CrmOrderProperties {

  private boolean enabled = false;
  private boolean failOpen = true;
  private String baseUrl = "http://localhost:8083";
  /** Numeric shop org for X-Shop-Id / X-Tenant-Id when CRM tenant is non-numeric. */
  private String shopId = "";
  /** Fallback Retail customer id when lead convert externalId is missing. */
  private Long defaultCustomerId;
  /** Fallback product id for quote lines (no productId on CRM QuoteLine yet). */
  private Long defaultProductId;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public boolean isFailOpen() {
    return failOpen;
  }

  public void setFailOpen(boolean failOpen) {
    this.failOpen = failOpen;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public String getShopId() {
    return shopId;
  }

  public void setShopId(String shopId) {
    this.shopId = shopId;
  }

  public Long getDefaultCustomerId() {
    return defaultCustomerId;
  }

  public void setDefaultCustomerId(Long defaultCustomerId) {
    this.defaultCustomerId = defaultCustomerId;
  }

  public Long getDefaultProductId() {
    return defaultProductId;
  }

  public void setDefaultProductId(Long defaultProductId) {
    this.defaultProductId = defaultProductId;
  }
}
