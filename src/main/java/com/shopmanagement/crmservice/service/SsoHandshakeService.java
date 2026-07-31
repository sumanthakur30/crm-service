package com.shopmanagement.crmservice.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.shopmanagement.crmservice.config.CrmSsoProperties;

@Service
public class SsoHandshakeService {

  private static final long STATE_TTL_SECONDS = 600;

  private final CrmSsoProperties properties;
  private final ConcurrentHashMap<String, Instant> pendingStates = new ConcurrentHashMap<>();

  public SsoHandshakeService(CrmSsoProperties properties) {
    this.properties = properties;
  }

  public Map<String, Object> status() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("enabled", properties.isEnabled());
    m.put("provider", normalizeProvider());
    boolean metaConfigured =
        properties.getMetadataUrl() != null && !properties.getMetadataUrl().isBlank();
    m.put("metadataUrlConfigured", metaConfigured);
    m.put("clientIdConfigured", properties.getClientId() != null && !properties.getClientId().isBlank());
    m.put("handshake", handshakeState(metaConfigured));
    m.put("redirectPath", properties.getRedirectPath());
    return m;
  }

  public Map<String, Object> authorize() {
    if (!properties.isEnabled()) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "SSO disabled");
    }
    String provider = normalizeProvider();
    boolean stub = "STUB".equals(provider);
    boolean metaOk =
        stub || (properties.getMetadataUrl() != null && !properties.getMetadataUrl().isBlank());
    boolean clientOk = properties.getClientId() != null && !properties.getClientId().isBlank();
    if (!metaOk || !clientOk) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "SSO misconfigured");
    }
    purgeExpired();
    String state = UUID.randomUUID().toString().replace("-", "");
    pendingStates.put(state, Instant.now().plusSeconds(STATE_TTL_SECONDS));

    String redirectUri = trimSlash(properties.getPublicBaseUrl()) + ensureLeadingSlash(properties.getRedirectPath());
    String clientId = properties.getClientId();
    String authorizeUrl =
        "https://sso.example/authorize?client_id="
            + enc(clientId)
            + "&state="
            + enc(state)
            + "&redirect_uri="
            + enc(redirectUri)
            + "&response_type=code";

    Map<String, Object> m = new LinkedHashMap<>();
    m.put("authorizeUrl", authorizeUrl);
    m.put("state", state);
    m.put("provider", provider);
    m.put("expiresInSec", STATE_TTL_SECONDS);
    return m;
  }

  public Map<String, Object> callback(String code, String state) {
    if (!properties.isEnabled()) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "SSO disabled");
    }
    purgeExpired();
    if (state == null || state.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "state required");
    }
    Instant exp = pendingStates.remove(state);
    if (exp == null || Instant.now().isAfter(exp)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired state");
    }
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("status", "STUB_OK");
    m.put("subject", "stub-user");
    m.put("codePresent", code != null && !code.isBlank());
    m.put("message", "Wire auth-service SAML/OIDC in deploy");
    return m;
  }

  private String handshakeState(boolean metaConfigured) {
    if (!properties.isEnabled()) {
      return "DISABLED";
    }
    boolean clientOk = properties.getClientId() != null && !properties.getClientId().isBlank();
    String provider = normalizeProvider();
    if ("STUB".equals(provider)) {
      return clientOk ? "READY" : "MISCONFIGURED";
    }
    return metaConfigured && clientOk ? "READY" : "MISCONFIGURED";
  }

  private String normalizeProvider() {
    String p = properties.getProvider();
    if (p == null || p.isBlank()) {
      return "STUB";
    }
    return p.trim().toUpperCase(Locale.ROOT);
  }

  private void purgeExpired() {
    Instant now = Instant.now();
    Iterator<Map.Entry<String, Instant>> it = pendingStates.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<String, Instant> e = it.next();
      if (now.isAfter(e.getValue())) {
        it.remove();
      }
    }
  }

  private static String enc(String v) {
    return URLEncoder.encode(v == null ? "" : v, StandardCharsets.UTF_8);
  }

  private static String trimSlash(String base) {
    if (base == null || base.isBlank()) {
      return "http://localhost:8095";
    }
    return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
  }

  private static String ensureLeadingSlash(String path) {
    if (path == null || path.isBlank()) {
      return "/api/v1/crm/sso/callback";
    }
    return path.startsWith("/") ? path : "/" + path;
  }
}
