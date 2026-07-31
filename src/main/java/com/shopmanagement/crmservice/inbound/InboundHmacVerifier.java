package com.shopmanagement.crmservice.inbound;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HMAC-SHA256 verifier for inbound lead-ad webhooks. Accepts raw hex digests or {@code sha256=}
 * prefixed values (Meta X-Hub-Signature-256 style).
 */
public final class InboundHmacVerifier {

  private InboundHmacVerifier() {}

  public static String signHex(byte[] body, String secret) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(body));
    } catch (Exception ex) {
      throw new IllegalStateException("HMAC-SHA256 unavailable", ex);
    }
  }

  public static boolean matches(byte[] body, String secret, String headerValue) {
    if (body == null || secret == null || secret.isBlank() || headerValue == null || headerValue.isBlank()) {
      return false;
    }
    String provided = normalize(headerValue);
    String expected = signHex(body, secret);
    byte[] a = expected.getBytes(StandardCharsets.UTF_8);
    byte[] b = provided.getBytes(StandardCharsets.UTF_8);
    return a.length == b.length && MessageDigest.isEqual(a, b);
  }

  /** Strip optional {@code sha256=} / {@code SHA256=} prefix and lowercase hex. */
  public static String normalize(String headerValue) {
    String v = headerValue.trim();
    int eq = v.indexOf('=');
    if (eq > 0) {
      String prefix = v.substring(0, eq).trim().toLowerCase(Locale.ROOT);
      if ("sha256".equals(prefix) || "sha-256".equals(prefix)) {
        v = v.substring(eq + 1).trim();
      }
    }
    return v.toLowerCase(Locale.ROOT);
  }
}
