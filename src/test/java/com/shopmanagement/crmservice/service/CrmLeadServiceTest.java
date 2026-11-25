package com.shopmanagement.crmservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import com.shopmanagement.crmservice.api.CrmLeadApi.LeadUpsert;
import com.shopmanagement.crmservice.filter.TenantContextFilter;
import com.shopmanagement.crmservice.persistence.entity.CrmLeadEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmPipelineEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmStageEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmLeadRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmPipelineRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmStageRepository;
import com.shopmanagement.crmservice.security.CrmRecordScopeService;

@ExtendWith(MockitoExtension.class)
class CrmLeadServiceTest {

  @Mock private CrmLeadRepository leadRepository;
  @Mock private CrmPipelineRepository pipelineRepository;
  @Mock private CrmStageRepository stageRepository;
  @Mock private com.shopmanagement.crmservice.persistence.repo.CrmCampaignRepository campaignRepository;
  @Mock private com.shopmanagement.crmservice.persistence.repo.CrmAccountRepository accountRepository;
  @Mock private com.shopmanagement.crmservice.persistence.repo.CrmContactRepository contactRepository;
  @Mock private WorkspaceBootstrapService workspaceBootstrapService;
  @Mock private TimelineService timelineService;
  @Mock private StageAutomationService stageAutomationService;
  @Mock private CrmRecordScopeService recordScopeService;
  @Mock private ScoreBandService scoreBandService;
  @Mock private FollowUpTaskService followUpTaskService;
  @Mock private LeadIdentityService leadIdentityService;
  @Mock private ObjectProvider<AssignmentService> assignmentProvider;
  @Mock private AssignmentService assignmentService;
  @Mock private LeadCaptureService leadCaptureService;
  @Mock private ConsultancyPackService consultancyPackService;

  private CrmLeadService leadService;

  @BeforeEach
  void bindTenant() {
    TenantContextFilter.bindTenantForTests("tenant-demo");
    leadService =
        new CrmLeadService(
            leadRepository,
            pipelineRepository,
            stageRepository,
            campaignRepository,
            accountRepository,
            contactRepository,
            workspaceBootstrapService,
            timelineService,
            stageAutomationService,
            recordScopeService,
            scoreBandService,
            followUpTaskService,
            leadIdentityService,
            assignmentProvider,
            leadCaptureService,
            consultancyPackService);
    lenient().when(leadIdentityService.findOpenMatch(any(), any(), any())).thenReturn(Optional.empty());
    lenient().when(assignmentProvider.getObject()).thenReturn(assignmentService);
    lenient().when(assignmentService.tryRoundRobin(anyLong())).thenReturn(null);
    lenient().when(consultancyPackService.newEnquiryOnRepeat(any())).thenReturn(false);
    lenient().when(consultancyPackService.ruleOwner(any(), any(), any(), any())).thenReturn(Optional.empty());
  }

  @AfterEach
  void clearTenant() {
    TenantContextFilter.clearTenantForTests();
  }

  @Test
  void create_usesDefaultPipelineAndFirstStage() {
    CrmPipelineEntity pipeline = new CrmPipelineEntity();
    pipeline.setId(10L);
    pipeline.setTenantId("tenant-demo");
    pipeline.setCode("SALES");

    CrmStageEntity stage = new CrmStageEntity();
    stage.setId(20L);
    stage.setTenantId("tenant-demo");
    stage.setPipelineId(10L);
    stage.setCode("NEW");

    when(workspaceBootstrapService.ensureDefaultPipeline()).thenReturn(pipeline);
    when(stageRepository.findFirstByTenantIdAndPipelineIdAndDeletedAtIsNullOrderBySortOrderAsc(
            "tenant-demo", 10L))
        .thenReturn(Optional.of(stage));
    when(leadRepository.save(any(CrmLeadEntity.class)))
        .thenAnswer(
            inv -> {
              CrmLeadEntity e = inv.getArgument(0);
              e.setId(99L);
              return e;
            });

    var response =
        leadService.create(
            new LeadUpsert(
                "Acme inquiry",
                "Ravi",
                "Acme",
                "ravi@acme.test",
                "9999999999",
                "WEBSITE",
                null,
                "HOT",
                40,
                null,
                null,
                null,
                null,
                null,
                null,
                MapAttrs.empty(),
                MapAttrs.empty(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null));

    assertThat(response.id()).isEqualTo(99L);
    assertThat(response.pipelineId()).isEqualTo(10L);
    assertThat(response.stageId()).isEqualTo(20L);
    assertThat(response.priority()).isEqualTo("HOT");
    assertThat(response.status()).isEqualTo("OPEN");

    ArgumentCaptor<CrmLeadEntity> captor = ArgumentCaptor.forClass(CrmLeadEntity.class);
    verify(leadRepository).save(captor.capture());
    assertThat(captor.getValue().getTenantId()).isEqualTo("tenant-demo");
    assertThat(captor.getValue().getOwnerUserId()).isNull();
    verify(leadCaptureService).recordNew(any(), any());
  }

  @Test
  void create_matchingPhoneDoesNotInsertAnotherLead() {
    CrmLeadEntity existing = new CrmLeadEntity();
    existing.setId(7L);
    existing.setTenantId("tenant-demo");
    existing.setTitle("Existing");
    existing.setPhone("9999999999");
    existing.setStatus("OPEN");
    when(leadIdentityService.findOpenMatch(any(), any(), any())).thenReturn(Optional.of(existing));

    var response =
        leadService.create(
            new LeadUpsert(
                "Second enquiry",
                "Ravi",
                null,
                "ravi@acme.test",
                "+91 99999 99999",
                "META",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                MapAttrs.empty(),
                MapAttrs.empty(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null));

    assertThat(response.id()).isEqualTo(7L);
    verify(leadRepository, never()).save(any());
    verify(assignmentService, never()).tryRoundRobin(anyLong());
    verify(leadCaptureService).recordRepeat(eq(existing), any());
    verify(leadCaptureService, never()).recordNew(any(), any());
  }

  /** Tiny helper so test constructors stay readable. */
  private static final class MapAttrs {
    static java.util.Map<String, Object> empty() {
      return java.util.Map.of();
    }
  }
}
