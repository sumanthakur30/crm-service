package com.shopmanagement.crmservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "crm.entitlement")
public class CrmProperties {

  /** When false (local default), feature flags are not checked. */
  private boolean enabled = false;

  private String baseUrl = "http://localhost:8182";

  private String flag = "FEATURE_CRM";

  private String quoteFlag = "FEATURE_CRM_QUOTE";
  private String campaignFlag = "FEATURE_CRM_CAMPAIGN";
  private String aiFlag = "FEATURE_CRM_AI";
  private String sequencesFlag = "FEATURE_CRM_SEQUENCES";
  private String whatsappFlag = "FEATURE_CRM_WHATSAPP";
  private String smsFlag = "FEATURE_CRM_SMS";
  private String emailFlag = "FEATURE_CRM_EMAIL";
  private String approvalFlag = "FEATURE_CRM_APPROVAL";
  private String automationFlag = "FEATURE_CRM_AUTOMATION";
  private String apiFlag = "FEATURE_CRM_API";
  private String casesFlag = "FEATURE_CRM_CASES";

  /** If subscription-service is unreachable, allow request when true. */
  private boolean failOpen = false;

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

  public String getFlag() {
    return flag;
  }

  public void setFlag(String flag) {
    this.flag = flag;
  }

  public String getQuoteFlag() {
    return quoteFlag;
  }

  public void setQuoteFlag(String quoteFlag) {
    this.quoteFlag = quoteFlag;
  }

  public String getCampaignFlag() {
    return campaignFlag;
  }

  public void setCampaignFlag(String campaignFlag) {
    this.campaignFlag = campaignFlag;
  }

  public String getAiFlag() {
    return aiFlag;
  }

  public void setAiFlag(String aiFlag) {
    this.aiFlag = aiFlag;
  }

  public String getSequencesFlag() {
    return sequencesFlag;
  }

  public void setSequencesFlag(String sequencesFlag) {
    this.sequencesFlag = sequencesFlag;
  }

  public String getWhatsappFlag() {
    return whatsappFlag;
  }

  public void setWhatsappFlag(String whatsappFlag) {
    this.whatsappFlag = whatsappFlag;
  }

  public String getSmsFlag() {
    return smsFlag;
  }

  public void setSmsFlag(String smsFlag) {
    this.smsFlag = smsFlag;
  }

  public String getEmailFlag() {
    return emailFlag;
  }

  public void setEmailFlag(String emailFlag) {
    this.emailFlag = emailFlag;
  }

  public String getApprovalFlag() {
    return approvalFlag;
  }

  public void setApprovalFlag(String approvalFlag) {
    this.approvalFlag = approvalFlag;
  }

  public String getAutomationFlag() {
    return automationFlag;
  }

  public void setAutomationFlag(String automationFlag) {
    this.automationFlag = automationFlag;
  }

  public String getApiFlag() {
    return apiFlag;
  }

  public void setApiFlag(String apiFlag) {
    this.apiFlag = apiFlag;
  }

  public String getCasesFlag() {
    return casesFlag;
  }

  public void setCasesFlag(String casesFlag) {
    this.casesFlag = casesFlag;
  }

  public boolean isFailOpen() {
    return failOpen;
  }

  public void setFailOpen(boolean failOpen) {
    this.failOpen = failOpen;
  }
}
