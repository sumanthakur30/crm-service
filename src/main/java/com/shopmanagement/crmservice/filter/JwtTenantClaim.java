package com.shopmanagement.crmservice.filter;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads {@code tenantId} from a Bearer token payload. The gateway already checked the signature
 * before the request reached CRM. This does not verify the token again.
 */
public final class JwtTenantClaim {

  private static final Pattern TENANT_ID =
      Pattern.compile("\"tenantId\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)");

  private JwtTenantClaim() {}

  /** Positive numeric tenant, or null when the header is missing, unsigned, or zero. */
  public static Long positiveTenantId(String authorization) {
    if (authorization == null) {
      return null;
    }
    String value = authorization.trim();
    if (value.length() < 8 || !value.regionMatches(true, 0, "Bearer ", 0, 7)) {
      return null;
    }
    String token = value.substring(7).trim();
    int firstDot = token.indexOf('.');
    int secondDot = firstDot < 0 ? -1 : token.indexOf('.', firstDot + 1);
    if (firstDot < 0 || secondDot <= firstDot) {
      return null;
    }
    try {
      String payload = token.substring(firstDot + 1, secondDot);
      int mod = payload.length() % 4;
      if (mod != 0) {
        payload = payload + "====".substring(mod);
      }
      byte[] json = Base64.getUrlDecoder().decode(payload);
      Matcher matcher = TENANT_ID.matcher(new String(json, StandardCharsets.UTF_8));
      if (!matcher.find()) {
        return null;
      }
      long tenantId = (long) Double.parseDouble(matcher.group(1));
      return tenantId > 0 ? tenantId : null;
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }
}
