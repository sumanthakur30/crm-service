package com.shopmanagement.crmservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shopmanagement.crmservice.ai.HeuristicLlmProvider;
import com.shopmanagement.crmservice.config.CrmAiProperties;
import com.shopmanagement.crmservice.filter.TenantContextFilter;
import com.shopmanagement.crmservice.persistence.entity.CrmAiInsightEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmLeadEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmAiInsightRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmLeadRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmOpportunityRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmScoreEventRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmTenantEnterpriseRepository;

@ExtendWith(MockitoExtension.class)
class AiAssistServiceTest {

  @Mock private CrmAiInsightRepository insightRepository;
  @Mock private CrmLeadRepository leadRepository;
  @Mock private CrmOpportunityRepository opportunityRepository;
  @Mock private CrmScoreEventRepository scoreEventRepository;
  @Mock private CrmTenantEnterpriseRepository enterpriseRepository;
  @Mock private TimelineService timelineService;
  @Mock private ScoreBandService scoreBandService;
  @Mock private UsageMeterService usageMeterService;

  private AiAssistService service;

  @BeforeEach
  void setUp() {
    CrmAiProperties props = new CrmAiProperties();
    props.setEnabled(true);
    props.setModelCode("HEURISTIC_V1");
    when(scoreBandService.resolveBand(org.mockito.ArgumentMatchers.anyInt())).thenReturn("WARM");
    service =
        new AiAssistService(
            props,
            insightRepository,
            leadRepository,
            opportunityRepository,
            scoreEventRepository,
            enterpriseRepository,
            timelineService,
            new com.shopmanagement.crmservice.integration.AiHttpClient(
                new org.springframework.web.client.RestTemplate(), props),
            scoreBandService,
            new HeuristicLlmProvider(),
            usageMeterService);
    TenantContextFilter.bindTenantForTests("demo-crm");
  }

  @AfterEach
  void tearDown() {
    TenantContextFilter.clearTenantForTests();
  }

  @Test
  void nextBestActionPersistsNbaInsight() {
    CrmLeadEntity lead = new CrmLeadEntity();
    lead.setId(7L);
    lead.setTitle("Retail demo");
    lead.setStatus("OPEN");
    lead.setScore(45);
    lead.setPhone("9876543210");
    when(leadRepository.findByTenantIdAndIdAndDeletedAtIsNull("demo-crm", 7L))
        .thenReturn(Optional.of(lead));
    when(enterpriseRepository.findById("demo-crm")).thenReturn(Optional.empty());
    when(insightRepository.save(any(CrmAiInsightEntity.class)))
        .thenAnswer(
            inv -> {
              CrmAiInsightEntity e = inv.getArgument(0);
              e.setId(99L);
              return e;
            });

    Map<String, Object> result = service.nextBestAction(7L, "en");

    assertEquals("NBA", result.get("insightType"));
    assertTrue(String.valueOf(result.get("body")).contains("NBA:"));
    ArgumentCaptor<CrmAiInsightEntity> cap = ArgumentCaptor.forClass(CrmAiInsightEntity.class);
    verify(insightRepository).save(cap.capture());
    assertEquals("NBA", cap.getValue().getInsightType());
    verify(timelineService)
        .recordEvent(eq("LEAD"), eq(7L), eq("AI_NBA"), anyString(), anyMap());
  }
}
