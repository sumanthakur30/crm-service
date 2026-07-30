package com.shopmanagement.crmservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "crm.payment")
public class CrmPaymentProperties {

  /** Public base used to build stub payment links (Razorpay/UPI later). */
  private String linkBaseUrl = "http://localhost:4500/pay";

  private String provider = "STUB";

  public String getLinkBaseUrl() {
    return linkBaseUrl;
  }

  public void setLinkBaseUrl(String linkBaseUrl) {
    this.linkBaseUrl = linkBaseUrl;
  }

  public String getProvider() {
    return provider;
  }

  public void setProvider(String provider) {
    this.provider = provider;
  }
}
