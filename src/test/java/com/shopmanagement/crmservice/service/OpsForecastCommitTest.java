package com.shopmanagement.crmservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.shopmanagement.crmservice.filter.TenantContextFilter;
import com.shopmanagement.crmservice.persistence.entity.CrmForecastCommitEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmOpportunityEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmApprovalRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmCalendarEventRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmCallLogRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmForecastCommitRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmOpportunityRepository;

@ExtendWith(MockitoExtension.class)
class OpsForecastCommitTest {

  @Mock private CrmCalendarEventRepository calendarRepository;
  @Mock private CrmCallLogRepository callLogRepository;
  @Mock private CrmApprovalRepository approvalRepository;
  @Mock private CrmOpportunityRepository opportunityRepository;
  @Mock private CrmForecastCommitRepository forecastCommitRepository;
  @Mock private TimelineService timelineService;
  @Mock private BehaviorScoringService scoringService;
  @Mock private QuotationService quotationService;

  private OpsService service;

  @BeforeEach
  void setUp() {
    service =
        new OpsService(
            calendarRepository,
            callLogRepository,
            approvalRepository,
            opportunityRepository,
            forecastCommitRepository,
            timelineService,
            scoringService,
            quotationService);
    TenantContextFilter.bindTenantForTests("demo-crm");
  }

  @AfterEach
  void tearDown() {
    TenantContextFilter.clearTenantForTests();
  }

  @Test
  void upsertCommitUsesDemoUserFallback() {
    when(forecastCommitRepository.findByTenantIdAndUserIdAndPeriodYm(
            eq("demo-crm"), eq("demo-user"), eq("2026-07")))
        .thenReturn(Optional.empty());
    when(forecastCommitRepository.save(any()))
        .thenAnswer(
            inv -> {
              CrmForecastCommitEntity e = inv.getArgument(0);
              e.setId(9L);
              return e;
            });

    Map<String, Object> saved =
        service.upsertForecastCommit(
            Map.of("periodYm", "2026-07", "amount", 150000, "note", "Q2 push"));

    assertEquals(9L, saved.get("id"));
    assertEquals("demo-user", saved.get("userId"));
    assertEquals(new BigDecimal("150000"), saved.get("amount"));

    ArgumentCaptor<CrmForecastCommitEntity> cap = ArgumentCaptor.forClass(CrmForecastCommitEntity.class);
    verify(forecastCommitRepository).save(cap.capture());
    assertEquals("demo-crm", cap.getValue().getTenantId());
  }

  @Test
  void forecastAggregatesPipelineAndCommits() {
    CrmOpportunityEntity opp = new CrmOpportunityEntity();
    opp.setId(1L);
    opp.setName("Deal A");
    opp.setAmount(new BigDecimal("100000"));
    opp.setProbability(50);

    CrmForecastCommitEntity commit = new CrmForecastCommitEntity();
    commit.setId(2L);
    commit.setUserId("alice");
    commit.setPeriodYm("2026-07");
    commit.setAmount(new BigDecimal("25000"));
    commit.setCurrency("INR");

    when(opportunityRepository.search(
            eq("demo-crm"),
            eq("OPEN"),
            isNull(),
            isNull(),
            eq("ORG"),
            isNull(),
            anyList(),
            any(PageRequest.class)))
        .thenReturn(new PageImpl<>(List.of(opp)));
    when(forecastCommitRepository.findByTenantIdAndPeriodYmOrderByUpdatedAtDesc(
            eq("demo-crm"), eq("2026-07")))
        .thenReturn(List.of(commit));

    Map<String, Object> out = service.forecast("2026-07");

    assertEquals(new BigDecimal("100000"), out.get("pipelineAmount"));
    assertEquals(new BigDecimal("50000.00"), out.get("weightedForecast"));
    assertEquals(new BigDecimal("25000"), out.get("commitTotal"));
    @SuppressWarnings("unchecked")
    Map<String, Object> collab = (Map<String, Object>) out.get("collaborative");
    assertEquals(new BigDecimal("50000.00"), collab.get("pipelineWeighted"));
    assertEquals(new BigDecimal("25000"), collab.get("commitTotal"));
    assertEquals(new BigDecimal("75000.00"), collab.get("combined"));
  }
}
