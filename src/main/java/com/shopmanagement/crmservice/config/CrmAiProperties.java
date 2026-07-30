package com.shopmanagement.crmservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "crm.ai")
public class CrmAiProperties {

  /** When false, AI endpoints return 503. */
  private boolean enabled = true;

  /** HEURISTIC (default) or HTTP (optional LLM bridge). */
  private String provider = "HEURISTIC";

  private String httpUrl = "";

  private String modelCode = "HEURISTIC_V1";

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

  public String getHttpUrl() {
    return httpUrl;
  }

  public void setHttpUrl(String httpUrl) {
    this.httpUrl = httpUrl;
  }

  public String getModelCode() {
    return modelCode;
  }

  public void setModelCode(String modelCode) {
    this.modelCode = modelCode;
  }
}
