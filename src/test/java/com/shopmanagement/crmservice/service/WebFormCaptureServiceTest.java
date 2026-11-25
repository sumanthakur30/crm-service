package com.shopmanagement.crmservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.shopmanagement.crmservice.api.CrmLeadApi.LeadResponse;
import com.shopmanagement.crmservice.api.CrmLeadApi.LeadUpsert;
import com.shopmanagement.crmservice.persistence.entity.CrmInboundEventEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmWebFormEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmFieldMapRepository;
import com.shopmanagement.crmservice.service.CapturePipe.CaptureResult;

@ExtendWith(MockitoExtension.class)
class WebFormCaptureServiceTest {

  @Mock private CrmFieldMapRepository fieldMapRepository;
  @Mock private CapturePipe capturePipe;
  @Mock private CrmLeadService leadService;
  @InjectMocks private WebFormCaptureService service;

  @Test
  void repeatEnquiryKeepsTheOwnerEmptyAndUsesTheNewCampaign() {
    CrmWebFormEntity form = form(20L);
    when(fieldMapRepository.findByFormIdOrderByIdAsc(3L)).thenReturn(java.util.List.of());
    LeadResponse created = org.mockito.Mockito.mock(LeadResponse.class);
    when(leadService.create(any(), eq(true))).thenReturn(created);
    when(capturePipe.run(any(), any(), any(), nullable(Long.class), nullable(Long.class), any()))
        .thenAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              Function<CrmInboundEventEntity, LeadResponse> create = invocation.getArgument(5);
              CrmInboundEventEntity event = new CrmInboundEventEntity();
              event.setId(1L);
              return new CaptureResult(create.apply(event), false);
            });

    service.submit(form, Map.of("phone", "99999 99999", "name", "Asha"), "key-b");

    ArgumentCaptor<LeadUpsert> captor = ArgumentCaptor.forClass(LeadUpsert.class);
    verify(leadService).create(captor.capture(), eq(true));
    assertNull(captor.getValue().ownerUserId());
    assertEquals(20L, captor.getValue().campaignId());
    assertEquals("+919999999999", captor.getValue().phone());
    assertEquals("WEBSITE", captor.getValue().sourceCode());
  }

  @Test
  void honeypotDoesNotCreateALead() {
    CrmWebFormEntity form = form(null);
    service.submit(form, Map.of("phone", "9999999999", "company_website", "spam"), "key-c");
    verify(capturePipe, never()).run(any(), any(), any(), any(), any(), any());
  }

  @Test
  void missingMobileAndEmailIsRejected() {
    CrmWebFormEntity form = form(null);
    when(fieldMapRepository.findByFormIdOrderByIdAsc(3L)).thenReturn(java.util.List.of());
    assertThrows(
        ResponseStatusException.class,
        () -> service.submit(form, Map.of("name", "Asha"), null));
    verify(capturePipe, never()).run(any(), any(), any(), any(), any(), any());
  }

  private static CrmWebFormEntity form(Long campaignId) {
    CrmWebFormEntity form = new CrmWebFormEntity();
    form.setId(3L);
    form.setTenantId("CRM-DEMO-01");
    form.setPublicKey("wf_test");
    form.setCampaignId(campaignId);
    form.setStatus("ACTIVE");
    form.setHoneypotField("company_website");
    return form;
  }
}
