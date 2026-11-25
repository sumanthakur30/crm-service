package com.shopmanagement.crmservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.shopmanagement.crmservice.api.CrmLeadApi.LeadResponse;
import com.shopmanagement.crmservice.filter.TenantContextFilter;
import com.shopmanagement.crmservice.persistence.entity.CrmLeadEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmStageEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmLeadRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmStageRepository;

@ExtendWith(MockitoExtension.class)
class LeadOutcomeServiceTest {

  @Mock private CrmLeadRepository leadRepository;
  @Mock private CrmStageRepository stageRepository;
  @Mock private CrmLeadService leadService;
  @Mock private OpsService opsService;
  @Mock private TimelineService timelineService;
  @Mock private FollowUpTaskService followUpTaskService;

  private LeadOutcomeService service;

  @BeforeEach
  void setUp() {
    TenantContextFilter.bindTenantForTests("tenant-demo");
    service =
        new LeadOutcomeService(
            leadRepository, stageRepository, leadService, opsService, timelineService, followUpTaskService);
  }

  @AfterEach
  void tearDown() {
    TenantContextFilter.clearTenantForTests();
  }

  @Test
  void noAnswerWithoutAFollowUpIsRejected() {
    CrmLeadEntity lead = openLead();
    CrmStageEntity stage = openStage();
    when(leadRepository.findByTenantIdAndIdAndDeletedAtIsNull("tenant-demo", 7L)).thenReturn(Optional.of(lead));
    when(stageRepository.findByTenantIdAndIdAndDeletedAtIsNull("tenant-demo", 3L)).thenReturn(Optional.of(stage));

    assertThatThrownBy(() -> service.log(7L, "NO_ANSWER", "rang", null, null))
        .isInstanceOf(ResponseStatusException.class);
    verify(opsService, never()).logCall(any());
  }

  @Test
  void noAnswerSchedulesTomorrowAndDoesNotCountAsFirstResponse() {
    CrmLeadEntity lead = openLead();
    CrmStageEntity stage = openStage();
    when(leadRepository.findByTenantIdAndIdAndDeletedAtIsNull("tenant-demo", 7L)).thenReturn(Optional.of(lead));
    when(stageRepository.findByTenantIdAndIdAndDeletedAtIsNull("tenant-demo", 3L)).thenReturn(Optional.of(stage));
    when(leadRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(leadService.get(7L)).thenReturn(org.mockito.Mockito.mock(LeadResponse.class));

    service.log(7L, "no answer", null, "2026-11-26", null);

    assertThat(lead.getFirstResponseAt()).isNull();
    assertThat(lead.getAttributes().get("profile")).isInstanceOf(java.util.Map.class);
    verify(opsService).logCall(any());
    verify(followUpTaskService).syncLead(lead);
    verify(leadService, never()).moveStage(anyLong(), anyLong());
  }

  @Test
  void connectedCallStoresFirstResponseOnce() {
    CrmLeadEntity lead = openLead();
    CrmStageEntity stage = openStage();
    stage.setWon(true);
    when(leadRepository.findByTenantIdAndIdAndDeletedAtIsNull("tenant-demo", 7L)).thenReturn(Optional.of(lead));
    when(stageRepository.findByTenantIdAndIdAndDeletedAtIsNull("tenant-demo", 3L)).thenReturn(Optional.of(stage));
    when(leadRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(leadService.get(7L)).thenReturn(org.mockito.Mockito.mock(LeadResponse.class));

    service.log(7L, "CONNECTED", null, null, null);

    assertThat(lead.getFirstResponseAt()).isNotNull();
  }

  private static CrmLeadEntity openLead() {
    CrmLeadEntity lead = new CrmLeadEntity();
    lead.setId(7L);
    lead.setTenantId("tenant-demo");
    lead.setPipelineId(2L);
    lead.setStageId(3L);
    lead.setPhone("9999999999");
    lead.setTitle("Walk-in");
    return lead;
  }

  private static CrmStageEntity openStage() {
    CrmStageEntity stage = new CrmStageEntity();
    stage.setId(3L);
    stage.setPipelineId(2L);
    stage.setCode("INQUIRY");
    return stage;
  }
}
