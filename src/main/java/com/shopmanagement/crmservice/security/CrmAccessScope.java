package com.shopmanagement.crmservice.security;

/** Record visibility for leads / opportunities. */
public enum CrmAccessScope {
  /** Entire tenant (org/branch). */
  ORG,
  /** Own records + records on teams the user belongs to. */
  TEAM,
  /** Only records owned by the current user. */
  OWN;

  public static CrmAccessScope parse(String raw) {
    if (raw == null || raw.isBlank()) {
      return ORG;
    }
    try {
      return CrmAccessScope.valueOf(raw.trim().toUpperCase());
    } catch (IllegalArgumentException ex) {
      return ORG;
    }
  }
}
