package com.shopmanagement.crmservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "crm.meta")
public class CrmMetaProperties {

  private String appId = "";
  private String appSecret = "";
  private String verifyToken = "";
  private String graphVersion = "v21.0";
  private String redirectUri = "http://localhost:8095/api/v1/crm/public/integrations/meta/callback";
  private String uiReturnUrl = "http://localhost:4500/integrations";
  /** Base64 of 32 bytes. Page tokens are stored only as ciphertext. */
  private String encryptionKey = "";

  public String getAppId() {
    return appId;
  }

  public void setAppId(String appId) {
    this.appId = appId;
  }

  public String getAppSecret() {
    return appSecret;
  }

  public void setAppSecret(String appSecret) {
    this.appSecret = appSecret;
  }

  public String getVerifyToken() {
    return verifyToken;
  }

  public void setVerifyToken(String verifyToken) {
    this.verifyToken = verifyToken;
  }

  public String getGraphVersion() {
    return graphVersion;
  }

  public void setGraphVersion(String graphVersion) {
    this.graphVersion = graphVersion;
  }

  public String getRedirectUri() {
    return redirectUri;
  }

  public void setRedirectUri(String redirectUri) {
    this.redirectUri = redirectUri;
  }

  public String getUiReturnUrl() {
    return uiReturnUrl;
  }

  public void setUiReturnUrl(String uiReturnUrl) {
    this.uiReturnUrl = uiReturnUrl;
  }

  public String getEncryptionKey() {
    return encryptionKey;
  }

  public void setEncryptionKey(String encryptionKey) {
    this.encryptionKey = encryptionKey;
  }

  public boolean isConfigured() {
    return notBlank(appId) && notBlank(appSecret) && notBlank(verifyToken) && notBlank(encryptionKey);
  }

  private static boolean notBlank(String value) {
    return value != null && !value.isBlank();
  }
}
