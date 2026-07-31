package com.shopmanagement.crmservice.inbound;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopmanagement.crmservice.config.CrmInboundProperties;

import jakarta.servlet.http.HttpServletRequest;

/**
 * When {@code crm.inbound.signing-enabled=true}, requires HMAC-SHA256 of the raw body (preferred)
 * or UTF-8 JSON of the already-parsed Map matching the signature header.
 */
@Component
public class InboundSignatureGuard {

  private final CrmInboundProperties properties;
  private final ObjectMapper objectMapper;

  public InboundSignatureGuard(CrmInboundProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  public void verifyIfRequired(HttpServletRequest request, Map<String, Object> payload) {
    if (!properties.isSigningEnabled()) {
      return;
    }
    String secret = properties.getHmacSecret();
    if (secret == null || secret.isBlank()) {
      throw new InboundSignatureException("Inbound signing enabled but hmac secret is empty");
    }
    String header = resolveSignatureHeader(request);
    if (header == null || header.isBlank()) {
      throw new InboundSignatureException("Missing inbound signature header");
    }
    byte[] body = rawBody(request);
    if (body == null || body.length == 0) {
      // Fallback: signature over UTF-8 JSON of the parsed Map (same bytes if client sent compact JSON).
      try {
        body = objectMapper.writeValueAsBytes(payload == null ? Map.of() : payload);
      } catch (Exception ex) {
        throw new InboundSignatureException("Unable to canonicalize inbound payload for signature");
      }
    }
    if (!InboundHmacVerifier.matches(body, secret, header)) {
      throw new InboundSignatureException("Inbound HMAC signature invalid");
    }
  }

  private String resolveSignatureHeader(HttpServletRequest request) {
    String configured =
        properties.getSignatureHeader() == null || properties.getSignatureHeader().isBlank()
            ? "X-Crm-Signature"
            : properties.getSignatureHeader().trim();
    String v = request.getHeader(configured);
    if (v != null && !v.isBlank()) {
      return v;
    }
    // Meta / common alternate
    v = request.getHeader("X-Hub-Signature-256");
    if (v != null && !v.isBlank()) {
      return v;
    }
    if (!"X-Crm-Signature".equalsIgnoreCase(configured)) {
      v = request.getHeader("X-Crm-Signature");
      if (v != null && !v.isBlank()) {
        return v;
      }
    }
    return null;
  }

  private static byte[] rawBody(HttpServletRequest request) {
    if (request instanceof ContentCachingRequestWrapper wrapper) {
      byte[] cached = wrapper.getContentAsByteArray();
      if (cached != null && cached.length > 0) {
        return cached;
      }
    }
    return new byte[0];
  }

  /** Test helper — verify over explicit UTF-8 body string. */
  public static void verifyBytes(byte[] body, String secret, String header) {
    if (!InboundHmacVerifier.matches(body, secret, header)) {
      throw new InboundSignatureException("Inbound HMAC signature invalid");
    }
  }

  public static byte[] utf8(String s) {
    return s.getBytes(StandardCharsets.UTF_8);
  }
}
