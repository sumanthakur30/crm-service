package com.shopmanagement.crmservice.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;

class JwtTenantClaimTest {

  @Test
  void readsPositiveTenantFromBearerPayload() {
    assertEquals(21L, JwtTenantClaim.positiveTenantId(bearer("{\"tenantId\":21,\"shopId\":\"GEN-DEMO-01\"}")));
  }

  @Test
  void ignoresZeroMissingAndShopCodeClaims() {
    assertNull(JwtTenantClaim.positiveTenantId(bearer("{\"tenantId\":0}")));
    assertNull(JwtTenantClaim.positiveTenantId(bearer("{\"shopId\":\"GEN-DEMO-01\"}")));
    assertNull(JwtTenantClaim.positiveTenantId("Bearer not-a-jwt"));
    assertNull(JwtTenantClaim.positiveTenantId(null));
  }

  private static String bearer(String json) {
    String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    return "Bearer header." + payload + ".sig";
  }
}
