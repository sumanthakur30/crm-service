package com.shopmanagement.crmservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "crm.sso")
public class CrmSsoProperties {

  private boolean enabled = false;

  /** OIDC | SAML | STUB */
  private String provider = "STUB";

  private String metadataUrl = "";

  private String clientId = "";

  private String redirectPath = "/api/v1/crm/sso/callback";

  /** Base URL used to build redirect_uri (e.g. http://localhost:8095). */
  private String publicBaseUrl = "http://localhost:8095";

  /**
   * Authorize endpoint base (e.g. https://login.example.com/oauth/authorize). When blank, STUB uses
   * {@code https://sso.example/authorize}.
   */
  private String authorizeBaseUrl = "";

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

  public String getMetadataUrl() {
    return metadataUrl;
  }

  public void setMetadataUrl(String metadataUrl) {
    this.metadataUrl = metadataUrl;
  }

  public String getClientId() {
    return clientId;
  }

  public void setClientId(String clientId) {
    this.clientId = clientId;
  }

  public String getRedirectPath() {
    return redirectPath;
  }

  public void setRedirectPath(String redirectPath) {
    this.redirectPath = redirectPath;
  }

  public String getPublicBaseUrl() {
    return publicBaseUrl;
  }

  public void setPublicBaseUrl(String publicBaseUrl) {
    this.publicBaseUrl = publicBaseUrl;
  }

  public String getAuthorizeBaseUrl() {
    return authorizeBaseUrl;
  }

  public void setAuthorizeBaseUrl(String authorizeBaseUrl) {
    this.authorizeBaseUrl = authorizeBaseUrl;
  }
}
