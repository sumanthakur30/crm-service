package com.shopmanagement.crmservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shopmanagement.crmservice.persistence.entity.CrmLeadEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmOpportunityEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmTaskEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmTaskRepository;

@ExtendWith(MockitoExtension.class)
class FollowUpTaskServiceTest {

  @Mock private CrmTaskRepository taskRepository;
  @InjectMocks private FollowUpTaskService service;

  @Test
  void createsOneOpenTaskForTheFollowUpDate() {
    CrmLeadEntity lead = lead("2026-10-20");
    when(taskRepository
            .findByTenantIdAndRelatedTypeAndRelatedIdAndSourceCodeAndStatusAndDeletedAtIsNullOrderByIdAsc(
                "tenant-demo", "LEAD", 7L, FollowUpTaskService.SOURCE_LEAD_FOLLOW_UP, "OPEN"))
        .thenReturn(List.of());

    service.syncLead(lead);

    ArgumentCaptor<CrmTaskEntity> captor = ArgumentCaptor.forClass(CrmTaskEntity.class);
    verify(taskRepository).save(captor.capture());
    CrmTaskEntity saved = captor.getValue();
    assertThat(saved.getId()).isNull();
    assertThat(saved.getSourceCode()).isEqualTo("LEAD_FOLLOW_UP");
    assertThat(saved.getStatus()).isEqualTo("OPEN");
    assertThat(saved.getRelatedId()).isEqualTo(7L);
    assertThat(saved.getDueAt()).isEqualTo(FollowUpTaskService.dueAtEndOfDay(LocalDate.parse("2026-10-20")));
    assertThat(saved.getTitle()).isEqualTo("Follow up: Acme inquiry");
  }

  @Test
  void secondSaveMovesTheSameTask() {
    CrmLeadEntity lead = lead("2026-10-22");
    CrmTaskEntity existing = openTask();
    existing.setDueAt(FollowUpTaskService.dueAtEndOfDay(LocalDate.parse("2026-10-20")));
    when(taskRepository
            .findByTenantIdAndRelatedTypeAndRelatedIdAndSourceCodeAndStatusAndDeletedAtIsNullOrderByIdAsc(
                "tenant-demo", "LEAD", 7L, FollowUpTaskService.SOURCE_LEAD_FOLLOW_UP, "OPEN"))
        .thenReturn(List.of(existing));

    service.syncLead(lead);

    assertThat(existing.getId()).isEqualTo(4L);
    assertThat(existing.getDueAt()).isEqualTo(FollowUpTaskService.dueAtEndOfDay(LocalDate.parse("2026-10-22")));
    assertThat(existing.getStatus()).isEqualTo("OPEN");
    verify(taskRepository).save(existing);
  }

  @Test
  void unchangedDateDoesNotWriteAgain() {
    CrmLeadEntity lead = lead("2026-10-20");
    CrmTaskEntity existing = openTask();
    existing.setDueAt(FollowUpTaskService.dueAtEndOfDay(LocalDate.parse("2026-10-20")));
    existing.setTitle("Follow up: Acme inquiry");
    existing.setPriority("HIGH");
    existing.setOwnerUserId("user-1");
    when(taskRepository
            .findByTenantIdAndRelatedTypeAndRelatedIdAndSourceCodeAndStatusAndDeletedAtIsNullOrderByIdAsc(
                "tenant-demo", "LEAD", 7L, FollowUpTaskService.SOURCE_LEAD_FOLLOW_UP, "OPEN"))
        .thenReturn(List.of(existing));

    service.syncLead(lead);

    verify(taskRepository, never()).save(any());
  }

  @Test
  void clearingTheDateCancelsTheOpenTask() {
    CrmLeadEntity lead = lead(null);
    CrmTaskEntity existing = openTask();
    when(taskRepository
            .findByTenantIdAndRelatedTypeAndRelatedIdAndSourceCodeAndStatusAndDeletedAtIsNullOrderByIdAsc(
                "tenant-demo", "LEAD", 7L, FollowUpTaskService.SOURCE_LEAD_FOLLOW_UP, "OPEN"))
        .thenReturn(new ArrayList<>(List.of(existing)));

    service.syncLead(lead);

    assertThat(existing.getStatus()).isEqualTo("CANCELLED");
    verify(taskRepository).save(existing);
  }

  @Test
  void extraOpenFollowUpsAreCancelled() {
    CrmLeadEntity lead = lead("2026-10-20");
    CrmTaskEntity keep = openTask();
    keep.setDueAt(FollowUpTaskService.dueAtEndOfDay(LocalDate.parse("2026-10-20")));
    keep.setTitle("Follow up: Acme inquiry");
    keep.setPriority("HIGH");
    keep.setOwnerUserId("user-1");
    CrmTaskEntity extra = openTask();
    extra.setId(5L);
    when(taskRepository
            .findByTenantIdAndRelatedTypeAndRelatedIdAndSourceCodeAndStatusAndDeletedAtIsNullOrderByIdAsc(
                "tenant-demo", "LEAD", 7L, FollowUpTaskService.SOURCE_LEAD_FOLLOW_UP, "OPEN"))
        .thenReturn(List.of(keep, extra));

    service.syncLead(lead);

    assertThat(keep.getStatus()).isEqualTo("OPEN");
    assertThat(extra.getStatus()).isEqualTo("CANCELLED");
    verify(taskRepository).save(extra);
    verify(taskRepository, never()).save(keep);
  }

  @Test
  void createsOneOpenTaskForADealFollowUp() {
    CrmOpportunityEntity deal = new CrmOpportunityEntity();
    deal.setId(8L);
    deal.setTenantId("tenant-demo");
    deal.setName("Acme order");
    deal.setOwnerUserId("user-2");
    deal.setAttributes(Map.of("profile", Map.of("nextFollowUpDate", "2026-10-21")));
    when(taskRepository
            .findByTenantIdAndRelatedTypeAndRelatedIdAndSourceCodeAndStatusAndDeletedAtIsNullOrderByIdAsc(
                "tenant-demo",
                "OPPORTUNITY",
                8L,
                FollowUpTaskService.SOURCE_OPPORTUNITY_FOLLOW_UP,
                "OPEN"))
        .thenReturn(List.of());

    service.syncOpportunity(deal);

    ArgumentCaptor<CrmTaskEntity> captor = ArgumentCaptor.forClass(CrmTaskEntity.class);
    verify(taskRepository).save(captor.capture());
    CrmTaskEntity saved = captor.getValue();
    assertThat(saved.getRelatedType()).isEqualTo("OPPORTUNITY");
    assertThat(saved.getRelatedId()).isEqualTo(8L);
    assertThat(saved.getSourceCode()).isEqualTo("OPPORTUNITY_FOLLOW_UP");
    assertThat(saved.getTitle()).isEqualTo("Follow up: Acme order");
    assertThat(saved.getDueAt()).isEqualTo(FollowUpTaskService.dueAtEndOfDay(LocalDate.parse("2026-10-21")));
  }

  @Test
  void secondDealSaveMovesTheSameTask() {
    CrmOpportunityEntity deal = new CrmOpportunityEntity();
    deal.setId(8L);
    deal.setTenantId("tenant-demo");
    deal.setName("Acme order");
    deal.setOwnerUserId("user-2");
    deal.setAttributes(Map.of("profile", Map.of("nextFollowUpDate", "2026-10-23")));
    CrmTaskEntity existing = openTask();
    existing.setRelatedType("OPPORTUNITY");
    existing.setRelatedId(8L);
    existing.setSourceCode(FollowUpTaskService.SOURCE_OPPORTUNITY_FOLLOW_UP);
    existing.setDueAt(FollowUpTaskService.dueAtEndOfDay(LocalDate.parse("2026-10-21")));
    when(taskRepository
            .findByTenantIdAndRelatedTypeAndRelatedIdAndSourceCodeAndStatusAndDeletedAtIsNullOrderByIdAsc(
                "tenant-demo",
                "OPPORTUNITY",
                8L,
                FollowUpTaskService.SOURCE_OPPORTUNITY_FOLLOW_UP,
                "OPEN"))
        .thenReturn(List.of(existing));

    service.syncOpportunity(deal);

    assertThat(existing.getId()).isEqualTo(4L);
    assertThat(existing.getStatus()).isEqualTo("OPEN");
    assertThat(existing.getDueAt()).isEqualTo(FollowUpTaskService.dueAtEndOfDay(LocalDate.parse("2026-10-23")));
    verify(taskRepository).save(existing);
  }

  @Test
  void clearingADealFollowUpCancelsTheOpenTask() {
    CrmOpportunityEntity deal = new CrmOpportunityEntity();
    deal.setId(8L);
    deal.setTenantId("tenant-demo");
    deal.setName("Acme order");
    deal.setAttributes(new LinkedHashMap<>());
    CrmTaskEntity existing = openTask();
    existing.setRelatedType("OPPORTUNITY");
    existing.setRelatedId(8L);
    existing.setSourceCode(FollowUpTaskService.SOURCE_OPPORTUNITY_FOLLOW_UP);
    when(taskRepository
            .findByTenantIdAndRelatedTypeAndRelatedIdAndSourceCodeAndStatusAndDeletedAtIsNullOrderByIdAsc(
                "tenant-demo",
                "OPPORTUNITY",
                8L,
                FollowUpTaskService.SOURCE_OPPORTUNITY_FOLLOW_UP,
                "OPEN"))
        .thenReturn(new ArrayList<>(List.of(existing)));

    service.syncOpportunity(deal);

    assertThat(existing.getStatus()).isEqualTo("CANCELLED");
    verify(taskRepository).save(existing);
  }

  @Test
  void dueInstantIsTheLastSecondOfThatKolkataDay() {
    Instant due = FollowUpTaskService.dueAtEndOfDay(LocalDate.parse("2026-10-20"));
    assertThat(due).isEqualTo(Instant.parse("2026-10-20T18:29:59Z"));
  }

  private static CrmLeadEntity lead(String followUpDate) {
    CrmLeadEntity lead = new CrmLeadEntity();
    lead.setId(7L);
    lead.setTenantId("tenant-demo");
    lead.setTitle("Acme inquiry");
    lead.setPriority("HIGH");
    lead.setOwnerUserId("user-1");
    if (followUpDate != null) {
      lead.setAttributes(Map.of("profile", Map.of("nextFollowUpDate", followUpDate)));
    } else {
      lead.setAttributes(new LinkedHashMap<>());
    }
    return lead;
  }

  private static CrmTaskEntity openTask() {
    CrmTaskEntity task = new CrmTaskEntity();
    task.setId(4L);
    task.setTenantId("tenant-demo");
    task.setRelatedType("LEAD");
    task.setRelatedId(7L);
    task.setSourceCode(FollowUpTaskService.SOURCE_LEAD_FOLLOW_UP);
    task.setStatus("OPEN");
    task.setTitle("Follow up: old");
    task.setPriority("MEDIUM");
    return task;
  }
}
