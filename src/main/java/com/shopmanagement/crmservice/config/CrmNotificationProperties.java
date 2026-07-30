package com.shopmanagement.crmservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "crm.notification")
public class CrmNotificationProperties {

  /** When false, quote send only marks SENT (no outbound queue). */
  private boolean enabled = false;

  private String baseUrl = "http://localhost:8087";

  /** If notification-service is down, still mark quote SENT when true. */
  private boolean failOpen = true;

  /** Built-in gallery codes resolved by notification-service. */
  private String quoteWhatsappTemplate = "CRM_QUOTE_WHATSAPP";
  private String quoteEmailTemplate = "CRM_QUOTE_EMAIL";
  private String quoteSmsTemplate = "CRM_QUOTE_SMS";
  private String sequenceStepTemplate = "CRM_SEQ_STEP";

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public boolean isFailOpen() {
    return failOpen;
  }

  public void setFailOpen(boolean failOpen) {
    this.failOpen = failOpen;
  }

  public String getQuoteWhatsappTemplate() {
    return quoteWhatsappTemplate;
  }

  public void setQuoteWhatsappTemplate(String quoteWhatsappTemplate) {
    this.quoteWhatsappTemplate = quoteWhatsappTemplate;
  }

  public String getQuoteEmailTemplate() {
    return quoteEmailTemplate;
  }

  public void setQuoteEmailTemplate(String quoteEmailTemplate) {
    this.quoteEmailTemplate = quoteEmailTemplate;
  }

  public String getQuoteSmsTemplate() {
    return quoteSmsTemplate;
  }

  public void setQuoteSmsTemplate(String quoteSmsTemplate) {
    this.quoteSmsTemplate = quoteSmsTemplate;
  }

  public String getSequenceStepTemplate() {
    return sequenceStepTemplate;
  }

  public void setSequenceStepTemplate(String sequenceStepTemplate) {
    this.sequenceStepTemplate = sequenceStepTemplate;
  }
}
