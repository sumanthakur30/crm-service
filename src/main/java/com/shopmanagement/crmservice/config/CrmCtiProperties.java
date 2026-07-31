package com.shopmanagement.crmservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "crm.cti")
public class CrmCtiProperties {

  /** When false, click-to-dial returns 503. */
  private boolean enabled = false;

  /** STUB by default — set HTTP to POST click-to-dial to webhook-url. */
  private String provider = "STUB";

  /** Target for {@code crm.cti.provider=HTTP} (Twilio Function / Exotel / custom). */
  private String webhookUrl = "";

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

  public String getWebhookUrl() {
    return webhookUrl;
  }

  public void setWebhookUrl(String webhookUrl) {
    this.webhookUrl = webhookUrl;
  }
}
