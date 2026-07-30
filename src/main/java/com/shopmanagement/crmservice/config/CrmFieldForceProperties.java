package com.shopmanagement.crmservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "crm.field-force")
public class CrmFieldForceProperties {

  /** When true, CRM UI can embed / deep-link Field Force visit screens. */
  private boolean embedEnabled = true;

  /**
   * Visit URL template. Placeholders: {{leadId}}, {{tenantId}}, {{phone}}. Example:
   * http://localhost:4300/visits?leadId={{leadId}}
   */
  private String visitUrlTemplate = "http://localhost:4300/visits?leadId={{leadId}}";

  private boolean openInNewTab = true;

  public boolean isEmbedEnabled() {
    return embedEnabled;
  }

  public void setEmbedEnabled(boolean embedEnabled) {
    this.embedEnabled = embedEnabled;
  }

  public String getVisitUrlTemplate() {
    return visitUrlTemplate;
  }

  public void setVisitUrlTemplate(String visitUrlTemplate) {
    this.visitUrlTemplate = visitUrlTemplate;
  }

  public boolean isOpenInNewTab() {
    return openInNewTab;
  }

  public void setOpenInNewTab(boolean openInNewTab) {
    this.openInNewTab = openInNewTab;
  }
}
