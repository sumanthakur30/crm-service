package com.shopmanagement.crmservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "crm.security")
public class CrmSecurityProperties {

  /**
   * When true, lead/opportunity reads and writes are limited by access scope (OWN / TEAM / ORG).
   * Local default false — tenant-wide access for pilot smoke.
   */
  private boolean recordScopeEnabled = false;

  /**
   * Default scope when header {@code X-Crm-Access-Scope} is absent and role does not map:
   * ORG | TEAM | OWN
   */
  private String defaultScope = "ORG";

  public boolean isRecordScopeEnabled() {
    return recordScopeEnabled;
  }

  public void setRecordScopeEnabled(boolean recordScopeEnabled) {
    this.recordScopeEnabled = recordScopeEnabled;
  }

  public String getDefaultScope() {
    return defaultScope;
  }

  public void setDefaultScope(String defaultScope) {
    this.defaultScope = defaultScope;
  }
}
