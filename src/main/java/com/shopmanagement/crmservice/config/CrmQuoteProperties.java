package com.shopmanagement.crmservice.config;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Quote versioning and discount-approval gate before send.
 *
 * <p>When header discount % of pre-discount taxable exceeds {@code discountApprovalThresholdPercent},
 * send is blocked until an APPROVED discount approval exists.
 */
@ConfigurationProperties(prefix = "crm.quote")
public class CrmQuoteProperties {

  /** When true, discount above threshold requires approval before send. */
  private boolean discountApprovalEnabled = true;

  /** Percent of (taxable + header discount). Default 10%. */
  private BigDecimal discountApprovalThresholdPercent = new BigDecimal("10");

  public boolean isDiscountApprovalEnabled() {
    return discountApprovalEnabled;
  }

  public void setDiscountApprovalEnabled(boolean discountApprovalEnabled) {
    this.discountApprovalEnabled = discountApprovalEnabled;
  }

  public BigDecimal getDiscountApprovalThresholdPercent() {
    return discountApprovalThresholdPercent;
  }

  public void setDiscountApprovalThresholdPercent(BigDecimal discountApprovalThresholdPercent) {
    this.discountApprovalThresholdPercent = discountApprovalThresholdPercent;
  }
}
