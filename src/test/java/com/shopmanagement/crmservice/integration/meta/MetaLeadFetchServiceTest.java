package com.shopmanagement.crmservice.integration.meta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shopmanagement.crmservice.api.CrmLeadApi.LeadResponse;
import com.shopmanagement.crmservice.api.CrmLeadApi.LeadUpsert;
import com.shopmanagement.crmservice.integration.IntegrationCipher;
import com.shopmanagement.crmservice.integration.meta.MetaGraphClient.LeadDetails;
import com.shopmanagement.crmservice.integration.meta.MetaGraphClient.LeadField;
import com.shopmanagement.crmservice.persistence.entity.CrmInboundEventEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmIntegrationAssetEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmIntegrationEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmIntegrationSecretEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmJobEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmFieldMapRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmInboundEventRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmIntegrationAssetRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmIntegrationRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmIntegrationSecretRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmJobRepository;
import com.shopmanagement.crmservice.service.CrmLeadService;

@ExtendWith(MockitoExtension.class)
class MetaLeadFetchServiceTest {

  @Mock private CrmJobRepository jobRepository;
  @Mock private CrmInboundEventRepository inboundRepository;
  @Mock private CrmIntegrationRepository integrationRepository;
  @Mock private CrmIntegrationAssetRepository assetRepository;
  @Mock private CrmIntegrationSecretRepository secretRepository;
  @Mock private CrmFieldMapRepository fieldMapRepository;
  @Mock private IntegrationCipher cipher;
  @Mock private MetaGraphClient graph;
  @Mock private CrmLeadService leadService;

  private MetaLeadFetchService service;
  private CrmJobEntity job;
  private CrmInboundEventEntity event;

  @BeforeEach
  void setUp() {
    service =
        new MetaLeadFetchService(
            jobRepository,
            inboundRepository,
            integrationRepository,
            assetRepository,
            secretRepository,
            fieldMapRepository,
            cipher,
            graph,
            leadService);
    job = new CrmJobEntity();
    job.setTenantId("CRM-DEMO-01");
    job.setJobType(MetaLeadFetchService.JOB_TYPE);
    job.setRefId(5L);
    job.setStatus("PENDING");
    job.setRunAt(Instant.now().minusSeconds(5));
    event = new CrmInboundEventEntity();
    event.setId(5L);
    event.setExternalId("99");
    event.setPayloadJson(
        Map.of(
            "entry",
            List.of(
                Map.of(
                    "changes",
                    List.of(
                        Map.of(
                            "field",
                            "leadgen",
                            "value",
                            Map.of("leadgen_id", "99", "page_id", "p1")))))));
  }

  @Test
  void fieldDataMapsNameAndMobileOntoTheSelectedBranch() {
    when(jobRepository.findByStatusAndRunAtLessThanEqual(eq("PENDING"), any(), any())).thenReturn(List.of(job));
    when(inboundRepository.findById(5L)).thenReturn(Optional.of(event));
    CrmIntegrationEntity integration = new CrmIntegrationEntity();
    integration.setId(3L);
    integration.setTenantId("CRM-DEMO-01");
    integration.setStatus("CONNECTED");
    when(integrationRepository.findByTenantIdAndProviderAndDeletedAtIsNull("CRM-DEMO-01", "META"))
        .thenReturn(Optional.of(integration));
    CrmIntegrationAssetEntity page = new CrmIntegrationAssetEntity();
    page.setId(8L);
    page.setIntegrationId(3L);
    page.setTenantId("CRM-DEMO-01");
    page.setExternalId("p1");
    page.setBranchId(7L);
    page.setSelected(true);
    when(assetRepository.findByAssetTypeAndExternalIdAndSelectedTrue("PAGE", "p1")).thenReturn(Optional.of(page));
    CrmIntegrationSecretEntity secret = new CrmIntegrationSecretEntity();
    secret.setCiphertext("cipher");
    when(secretRepository.findByAssetIdAndSecretKind(8L, "PAGE_TOKEN")).thenReturn(Optional.of(secret));
    when(cipher.decrypt("cipher")).thenReturn("page-token");
    CrmIntegrationAssetEntity form = new CrmIntegrationAssetEntity();
    form.setId(9L);
    form.setSelected(true);
    form.setBranchId(11L);
    when(assetRepository.findByIntegrationIdAndAssetTypeAndExternalId(3L, "FORM", "form1"))
        .thenReturn(Optional.of(form));
    when(fieldMapRepository.findByAssetIdOrderByIdAsc(9L)).thenReturn(List.of());
    when(graph.fetchLead(eq("99"), eq("page-token"), eq(false)))
        .thenReturn(
            new LeadDetails(
                "99",
                "form1",
                "c1",
                "Camp",
                "a1",
                "Ad",
                "s1",
                "Set",
                "p1",
                "ig-actor",
                List.of(
                    new LeadField("full_name", List.of("Asha")),
                    new LeadField("phone_number", List.of("9876543210")))));
    LeadResponse created = mock(LeadResponse.class);
    when(created.id()).thenReturn(44L);
    when(leadService.create(any(), eq(true))).thenReturn(created);

    service.processDue();
    service.processDue();

    ArgumentCaptor<LeadUpsert> saved = ArgumentCaptor.forClass(LeadUpsert.class);
    verify(leadService, times(1)).create(saved.capture(), eq(true));
    LeadUpsert body = saved.getValue();
    assertEquals("Asha", body.displayName());
    assertEquals("+919876543210", body.phone());
    assertNull(body.ownerUserId());
    assertEquals("FACEBOOK_LEAD_AD", body.sourceCode());
    assertEquals(11L, body.attributes().get("branchId"));
    assertEquals("ig-actor", body.externalRefs().get("instagramActorId"));
    assertEquals("MAPPED", event.getStatus());
    assertEquals(44L, event.getLeadId());
  }
}
