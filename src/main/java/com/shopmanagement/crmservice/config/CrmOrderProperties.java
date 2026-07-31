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
  /**
   * Numeric platform tenant for {@code X-Tenant-Id}. When blank, {@link #shopId} is used for both
   * headers (legacy). Prefer setting shopId=RET-DEMO-01 and tenantId=102 for Retail.
   */
  private String tenantId = "";
  /** Fallback Retail customer id when lead convert externalId is missing. */
  private Long defaultCustomerId;
  /** Fallback product id for quote lines (no productId on CRM QuoteLine yet). */
  private Long defaultProductId;
  /** Optional service-to-service key forwarded as {@code X-Internal-Api-Key}. */
  private String internalApiKey = "";

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

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
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

  public String getInternalApiKey() {
    return internalApiKey;
  }

  public void setInternalApiKey(String internalApiKey) {
    this.internalApiKey = internalApiKey;
  }
}
