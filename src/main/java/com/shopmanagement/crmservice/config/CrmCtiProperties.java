package com.shopmanagement.crmservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "crm.cti")
public class CrmCtiProperties {

  /** When false, click-to-dial returns 503. */
  private boolean enabled = false;

  /** STUB by default — swap for real softphone provider later. */
  private String provider = "STUB";

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getProvider() {
    return provider;
  }

  public void setProvider(String provider) {
    this.provider = provider;
  }
}
