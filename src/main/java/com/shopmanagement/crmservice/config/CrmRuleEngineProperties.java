package com.shopmanagement.crmservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Optional School rule-engine evaluate on CRM stage changes. Off by default; CRM-local stage
 * automation rules always run when entitlement allows.
 */
@ConfigurationProperties(prefix = "crm.rule-engine")
public class CrmRuleEngineProperties {

  private boolean enabled = false;
  private boolean failOpen = true;
  private String baseUrl = "http://localhost:8092";

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
}
