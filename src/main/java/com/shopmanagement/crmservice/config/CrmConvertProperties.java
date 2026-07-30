package com.shopmanagement.crmservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Lead → ERP convert. {@code SHOP_CUSTOMER} maps to <strong>user-service</strong> {@code
 * /api/v1/customers/from-crm} (Retail customers), not shop-service. Local pilot may point at {@code
 * ErpConvertSinkController} until live receivers are up.
 */
@ConfigurationProperties(prefix = "crm.convert")
public class CrmConvertProperties {

  /** When false, convert records SKIPPED and stores payload only. */
  private boolean enabled = false;

  /**
   * Live Retail default: user-service :8084. Pilot profile overrides to local CRM sink unless
   * {@code CRM_CONVERT_SHOP_URL} is set.
   */
  private String shopCustomerUrl = "http://localhost:8084/api/v1/customers/from-crm";

  private String schoolInquiryUrl = "http://localhost:8081/api/v1/inquiries/from-crm";
  private String fieldForceUrl = "http://localhost:8090/api/v1/leads/from-crm";
  private boolean failOpen = true;

  /**
   * Numeric shop org id for user-service {@code X-Shop-Id}. When blank, uses inbound {@code
   * X-Shop-Id} then falls back to {@code X-Tenant-Id}.
   */
  private String shopId = "";

  /** Optional service-to-service key forwarded as {@code X-Internal-Api-Key}. */
  private String internalApiKey = "";

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getShopCustomerUrl() {
    return shopCustomerUrl;
  }

  public void setShopCustomerUrl(String shopCustomerUrl) {
    this.shopCustomerUrl = shopCustomerUrl;
  }

  public String getSchoolInquiryUrl() {
    return schoolInquiryUrl;
  }

  public void setSchoolInquiryUrl(String schoolInquiryUrl) {
    this.schoolInquiryUrl = schoolInquiryUrl;
  }

  public String getFieldForceUrl() {
    return fieldForceUrl;
  }

  public void setFieldForceUrl(String fieldForceUrl) {
    this.fieldForceUrl = fieldForceUrl;
  }

  public boolean isFailOpen() {
    return failOpen;
  }

  public void setFailOpen(boolean failOpen) {
    this.failOpen = failOpen;
  }

  public String getShopId() {
    return shopId;
  }

  public void setShopId(String shopId) {
    this.shopId = shopId;
  }

  public String getInternalApiKey() {
    return internalApiKey;
  }

  public void setInternalApiKey(String internalApiKey) {
    this.internalApiKey = internalApiKey;
  }
}
