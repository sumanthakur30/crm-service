package com.shopmanagement.crmservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "crm.convert")
public class CrmConvertProperties {

  /** When false, convert records SKIPPED and stores payload only. */
  private boolean enabled = false;

  private String shopCustomerUrl = "http://localhost:8082/api/v1/customers/from-crm";
  private String schoolInquiryUrl = "http://localhost:8081/api/v1/inquiries/from-crm";
  private String fieldForceUrl = "http://localhost:8090/api/v1/leads/from-crm";
  private boolean failOpen = true;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getShopCustomerUrl() {
    return shopCustomerUrl;
  }

  public void setShopCustomerUrl(String shopCustomerUrl) {
    this.shopCustomerUrl = shopCustomerUrl;
  }

  public String getSchoolInquiryUrl() {
    return schoolInquiryUrl;
  }

  public void setSchoolInquiryUrl(String schoolInquiryUrl) {
    this.schoolInquiryUrl = schoolInquiryUrl;
  }

  public String getFieldForceUrl() {
    return fieldForceUrl;
  }

  public void setFieldForceUrl(String fieldForceUrl) {
    this.fieldForceUrl = fieldForceUrl;
  }

  public boolean isFailOpen() {
    return failOpen;
  }

  public void setFailOpen(boolean failOpen) {
    this.failOpen = failOpen;
  }
}
