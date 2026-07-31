package com.shopmanagement.crmservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "crm.inbound")
public class CrmInboundProperties {

  /** When true, public adapter webhooks require a valid HMAC signature. */
  private boolean signingEnabled = false;

  /** Shared secret for HMAC-SHA256. Empty when signing disabled (pilot). */
  private String hmacSecret = "";

  /** Primary signature header; also accepts X-Hub-Signature-256. */
  private String signatureHeader = "X-Crm-Signature";

  public boolean isSigningEnabled() {
    return signingEnabled;
  }

  public void setSigningEnabled(boolean signingEnabled) {
    this.signingEnabled = signingEnabled;
  }

  public String getHmacSecret() {
    return hmacSecret;
  }

  public void setHmacSecret(String hmacSecret) {
    this.hmacSecret = hmacSecret;
  }

  public String getSignatureHeader() {
    return signatureHeader;
  }

  public void setSignatureHeader(String signatureHeader) {
    this.signatureHeader = signatureHeader;
  }
}
