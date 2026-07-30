package com.shopmanagement.crmservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "crm.entitlement")
public class CrmProperties {

  /** When false (local default), FEATURE_CRM is not checked. */
  private boolean enabled = false;

  private String baseUrl = "http://localhost:8182";

  private String flag = "FEATURE_CRM";

  /** Optional quote gate; empty disables sub-flag check. */
  private String quoteFlag = "FEATURE_CRM_QUOTE";

  /** If subscription-service is unreachable, allow request when true. */
  private boolean failOpen = false;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public String getFlag() {
    return flag;
  }

  public void setFlag(String flag) {
    this.flag = flag;
  }

  public String getQuoteFlag() {
    return quoteFlag;
  }

  public void setQuoteFlag(String quoteFlag) {
    this.quoteFlag = quoteFlag;
  }

  public boolean isFailOpen() {
    return failOpen;
  }

  public void setFailOpen(boolean failOpen) {
    this.failOpen = failOpen;
  }
}
