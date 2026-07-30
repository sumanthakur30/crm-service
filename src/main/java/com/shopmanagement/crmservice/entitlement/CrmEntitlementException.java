package com.shopmanagement.crmservice.entitlement;

public class CrmEntitlementException extends RuntimeException {

  public CrmEntitlementException(String message) {
    super(message);
  }

  public CrmEntitlementException(String message, Throwable cause) {
    super(message, cause);
  }
}
