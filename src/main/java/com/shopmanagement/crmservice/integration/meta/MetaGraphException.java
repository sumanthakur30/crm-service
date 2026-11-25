package com.shopmanagement.crmservice.integration.meta;

public class MetaGraphException extends RuntimeException {

  private final int status;

  public MetaGraphException(int status, String message) {
    super(message == null || message.isBlank() ? "Facebook request failed" : message);
    this.status = status;
  }

  public int getStatus() {
    return status;
  }

  public boolean isPermissionFailure() {
    return status == 401 || status == 403 || status == 190;
  }
}
