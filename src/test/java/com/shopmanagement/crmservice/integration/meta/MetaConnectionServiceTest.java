package com.shopmanagement.crmservice.integration.meta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopmanagement.crmservice.config.CrmMetaProperties;
import com.shopmanagement.crmservice.filter.TenantContextFilter;
import com.shopmanagement.crmservice.integration.IntegrationCipher;
import com.shopmanagement.crmservice.integration.meta.MetaConnectionService.ConnectLink;
import com.shopmanagement.crmservice.integration.meta.MetaConnectionService.Desk;

class MetaConnectionServiceTest {

  @AfterEach
  void clearTenant() {
    TenantContextFilter.clearTenantForTests();
  }

  @Test
  void connectLinkHasNoPageToken() throws Exception {
    CrmMetaProperties properties = configured();
    MetaConnectionService service =
        new MetaConnectionService(properties, null, null, null, null, null, null, null);
    TenantContextFilter.bindTenantForTests("CRM-DEMO-01");
    ConnectLink link = service.connectLink();
    String json = new ObjectMapper().writeValueAsString(link);
    assertTrue(link.authorizeUrl().contains("client_id=app-id"));
    assertFalse(json.contains("access_token"));
    assertFalse(json.contains("ciphertext"));
    assertFalse(json.contains("app-secret"));
    assertFalse(link.authorizeUrl().contains("app-secret"));
  }

  @Test
  void deskJsonHasNoSecret() throws Exception {
    Desk desk =
        new Desk(
            "CONNECTED",
            false,
            false,
            "A test lead from Meta's tool can arrive after a page is selected.",
            null,
            List.of(),
            List.of(),
            List.of(),
            List.of());
    String json = new ObjectMapper().writeValueAsString(desk);
    assertFalse(desk.healthy());
    assertFalse(desk.productionReady());
    assertFalse(json.contains("access_token"));
    assertFalse(json.contains("ciphertext"));
    assertFalse(json.contains("appSecret"));
  }

  @Test
  void cipherRoundTripDoesNotStoreTheToken() {
    IntegrationCipher cipher = new IntegrationCipher(configured());
    String stored = cipher.encrypt("page-token-value");
    assertNotEquals("page-token-value", stored);
    assertFalse(stored.contains("page-token-value"));
    assertEquals("page-token-value", cipher.decrypt(stored));
  }

  @Test
  void missingEncryptionKeyIsRejected() {
    CrmMetaProperties properties = new CrmMetaProperties();
    IntegrationCipher cipher = new IntegrationCipher(properties);
    ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> cipher.encrypt("page-token"));
    assertEquals(503, ex.getStatusCode().value());
  }

  private static CrmMetaProperties configured() {
    CrmMetaProperties properties = new CrmMetaProperties();
    properties.setAppId("app-id");
    properties.setAppSecret("app-secret");
    properties.setVerifyToken("verify-token");
    properties.setEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));
    return properties;
  }
}
