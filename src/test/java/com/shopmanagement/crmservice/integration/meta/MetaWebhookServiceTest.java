package com.shopmanagement.crmservice.integration.meta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.shopmanagement.crmservice.config.CrmMetaProperties;
import com.shopmanagement.crmservice.inbound.InboundHmacVerifier;
import com.shopmanagement.crmservice.persistence.entity.CrmInboundEventEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmInboundEventRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmIntegrationAssetRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmIntegrationRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmJobRepository;

@ExtendWith(MockitoExtension.class)
class MetaWebhookServiceTest {

  @Mock private CrmInboundEventRepository inboundRepository;
  @Mock private CrmIntegrationAssetRepository assetRepository;
  @Mock private CrmIntegrationRepository integrationRepository;
  @Mock private CrmJobRepository jobRepository;

  private MetaWebhookService service;

  @BeforeEach
  void setUp() {
    CrmMetaProperties properties = new CrmMetaProperties();
    properties.setAppSecret("app-secret");
    service =
        new MetaWebhookService(
            properties, inboundRepository, assetRepository, integrationRepository, jobRepository);
  }

  @Test
  void badSignatureDoesNotInsertAnEvent() {
    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class,
            () -> service.receive("{\"object\":\"page\"}".getBytes(), "sha256=00", Map.of()));
    assertEquals(401, ex.getStatusCode().value());
    verify(inboundRepository, never()).save(any());
    verify(jobRepository, never()).save(any());
  }

  @Test
  void unknownPageIsStoredAndDoesNotCreateALead() {
    when(inboundRepository.findByProviderAndExternalId("META", "99")).thenReturn(Optional.empty());
    when(assetRepository.findByAssetTypeAndExternalIdAndSelectedTrue("PAGE", "p1")).thenReturn(Optional.empty());
    when(assetRepository.findByAssetTypeAndExternalId("PAGE", "p1")).thenReturn(List.of());
    byte[] body = "{\"leadgen\":true}".getBytes();
    service.receive(body, "sha256=" + InboundHmacVerifier.signHex(body, "app-secret"), payload("99", "p1"));

    ArgumentCaptor<CrmInboundEventEntity> saved = ArgumentCaptor.forClass(CrmInboundEventEntity.class);
    verify(inboundRepository).save(saved.capture());
    assertEquals("FAILED", saved.getValue().getStatus());
    assertEquals("page not selected", saved.getValue().getErrorMessage());
    verify(jobRepository, never()).save(any());
  }

  @Test
  void theSameLeadgenIdIsAcceptedOnce() {
    CrmInboundEventEntity existing = new CrmInboundEventEntity();
    existing.setExternalId("99");
    when(inboundRepository.findByProviderAndExternalId("META", "99")).thenReturn(Optional.of(existing));
    byte[] body = "{\"leadgen\":true}".getBytes();
    service.receive(body, "sha256=" + InboundHmacVerifier.signHex(body, "app-secret"), payload("99", "p1"));
    service.receive(body, "sha256=" + InboundHmacVerifier.signHex(body, "app-secret"), payload("99", "p1"));

    verify(inboundRepository, never()).save(any());
    verify(jobRepository, never()).save(any());
  }

  private static Map<String, Object> payload(String leadgenId, String pageId) {
    return Map.of(
        "entry",
        List.of(
            Map.of(
                "changes",
                List.of(Map.of("field", "leadgen", "value", Map.of("leadgen_id", leadgenId, "page_id", pageId))))));
  }
}
