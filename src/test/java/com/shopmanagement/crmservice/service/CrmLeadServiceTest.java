package com.shopmanagement.crmservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shopmanagement.crmservice.api.CrmLeadApi.LeadUpsert;
import com.shopmanagement.crmservice.filter.TenantContextFilter;
import com.shopmanagement.crmservice.persistence.entity.CrmLeadEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmPipelineEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmStageEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmLeadRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmPipelineRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmStageRepository;

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

  @InjectMocks private CrmLeadService leadService;

  @BeforeEach
  void bindTenant() {
    TenantContextFilter.bindTenantForTests("tenant-demo");
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
  }

  /** Tiny helper so test constructors stay readable. */
  private static final class MapAttrs {
    static java.util.Map<String, Object> empty() {
      return java.util.Map.of();
    }
  }
}
