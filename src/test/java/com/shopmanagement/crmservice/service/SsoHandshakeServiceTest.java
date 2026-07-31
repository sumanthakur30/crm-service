package com.shopmanagement.crmservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.shopmanagement.crmservice.config.CrmSsoProperties;

class SsoHandshakeServiceTest {

  private CrmSsoProperties properties;
  private SsoHandshakeService service;

  @BeforeEach
  void setUp() {
    properties = new CrmSsoProperties();
    properties.setEnabled(true);
    properties.setProvider("STUB");
    properties.setClientId("crm-stub-client");
    properties.setMetadataUrl("");
    properties.setPublicBaseUrl("http://localhost:8095");
    properties.setRedirectPath("/api/v1/crm/sso/callback");
    service = new SsoHandshakeService(properties);
  }

  @Test
  void statusReadyWhenStubConfigured() {
    Map<String, Object> s = service.status();
    assertEquals(true, s.get("enabled"));
    assertEquals("STUB", s.get("provider"));
    assertEquals("READY", s.get("handshake"));
  }

  @Test
  void authorizeUsesConfiguredBaseUrl() {
    properties.setAuthorizeBaseUrl("https://idp.example/oauth/authorize");
    Map<String, Object> auth = service.authorize();
    assertTrue(String.valueOf(auth.get("authorizeUrl")).startsWith("https://idp.example/oauth/authorize?"));
  }

  @Test
  void oidcReadyWhenMetadataAndClientSet() {
    properties.setProvider("OIDC");
    properties.setMetadataUrl("https://idp.example/.well-known/openid-configuration");
    Map<String, Object> s = service.status();
    assertEquals("READY", s.get("handshake"));
  }

  @Test
  void authorizeReturnsUrlAndCallbackValidatesState() {
    Map<String, Object> auth = service.authorize();
    String state = String.valueOf(auth.get("state"));
    assertTrue(String.valueOf(auth.get("authorizeUrl")).contains("state="));

    Map<String, Object> cb = service.callback("abc", state);
    assertEquals("STUB_OK", cb.get("status"));
    assertEquals("stub-user", cb.get("subject"));
  }

  @Test
  void authorizeWhenDisabledReturns503() {
    properties.setEnabled(false);
    ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.authorize());
    assertEquals(503, ex.getStatusCode().value());
  }

  @Test
  void callbackRejectsUnknownState() {
    ResponseStatusException ex =
        assertThrows(ResponseStatusException.class, () -> service.callback("x", "nope"));
    assertEquals(400, ex.getStatusCode().value());
  }
}
