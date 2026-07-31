package com.shopmanagement.crmservice.inbound;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopmanagement.crmservice.config.CrmInboundProperties;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.mock.web.MockHttpServletRequest;

class InboundHmacVerifierTest {

  @Test
  void validSignaturePasses() {
    byte[] body = "{\"name\":\"Ada\"}".getBytes(StandardCharsets.UTF_8);
    String secret = "test-secret";
    String sig = InboundHmacVerifier.signHex(body, secret);
    assertTrue(InboundHmacVerifier.matches(body, secret, sig));
    assertTrue(InboundHmacVerifier.matches(body, secret, "sha256=" + sig));
  }

  @Test
  void badSignatureFails() {
    byte[] body = "{\"name\":\"Ada\"}".getBytes(StandardCharsets.UTF_8);
    assertFalse(InboundHmacVerifier.matches(body, "test-secret", "deadbeef"));
  }

  @Test
  void guardRejectsWhenEnabledAndBadSig() throws Exception {
    CrmInboundProperties props = new CrmInboundProperties();
    props.setSigningEnabled(true);
    props.setHmacSecret("test-secret");
    props.setSignatureHeader("X-Crm-Signature");
    InboundSignatureGuard guard = new InboundSignatureGuard(props, new ObjectMapper());

    MockHttpServletRequest req = new MockHttpServletRequest();
    req.addHeader("X-Crm-Signature", "sha256=0000");
    Map<String, Object> payload = Map.of("name", "Ada");

    assertThrows(InboundSignatureException.class, () -> guard.verifyIfRequired(req, payload));
  }

  @Test
  void guardAllowsWhenDisabled() {
    CrmInboundProperties props = new CrmInboundProperties();
    props.setSigningEnabled(false);
    InboundSignatureGuard guard = new InboundSignatureGuard(props, new ObjectMapper());
    HttpServletRequest req = new MockHttpServletRequest();
    guard.verifyIfRequired(req, Map.of("name", "Ada"));
  }

  @Test
  void guardAcceptsValidCanonicalJsonWhenNoRawCache() throws Exception {
    CrmInboundProperties props = new CrmInboundProperties();
    props.setSigningEnabled(true);
    props.setHmacSecret("test-secret");
    ObjectMapper mapper = new ObjectMapper();
    InboundSignatureGuard guard = new InboundSignatureGuard(props, mapper);

    Map<String, Object> payload = Map.of("name", "Ada");
    byte[] canonical = mapper.writeValueAsBytes(payload);
    String sig = InboundHmacVerifier.signHex(canonical, "test-secret");

    MockHttpServletRequest req = new MockHttpServletRequest();
    req.addHeader("X-Crm-Signature", "sha256=" + sig);
    guard.verifyIfRequired(req, payload);
  }
}
