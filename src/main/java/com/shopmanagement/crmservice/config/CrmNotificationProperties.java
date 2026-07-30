package com.shopmanagement.crmservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "crm.notification")
public class CrmNotificationProperties {

  /** When false, quote send only marks SENT (no outbound queue). */
  private boolean enabled = false;

  private String baseUrl = "http://localhost:8087";

  /** If notification-service is down, still mark quote SENT when true. */
  private boolean failOpen = true;

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

  public boolean isFailOpen() {
    return failOpen;
  }

  public void setFailOpen(boolean failOpen) {
    this.failOpen = failOpen;
  }
}
