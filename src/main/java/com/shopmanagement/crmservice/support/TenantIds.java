package com.shopmanagement.crmservice.support;

import com.shopmanagement.crmservice.filter.TenantContextFilter;

public final class TenantIds {

  private TenantIds() {}

  public static String require() {
    String tenantId = TenantContextFilter.getCurrentTenantId();
    if (tenantId == null || tenantId.isBlank()) {
      throw new IllegalStateException("Missing tenant context");
    }
    return tenantId;
  }

  public static String currentUserOrNull() {
    return TenantContextFilter.getCurrentUserId();
  }

  /** Prefer X-User-Id; fall back to demo-user for local pilot like other CRM paths. */
  public static String currentUserOrDemo() {
    String u = currentUserOrNull();
    return u == null || u.isBlank() ? "demo-user" : u;
  }
}
