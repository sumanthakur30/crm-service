package com.shopmanagement.crmservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.shopmanagement.crmservice.api.CrmLeadApi.NoteRequest;
import com.shopmanagement.crmservice.api.CrmLeadApi.NoteResponse;
import com.shopmanagement.crmservice.filter.TenantContextFilter;
import com.shopmanagement.crmservice.persistence.entity.CrmNoteEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmOpportunityEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmAccountRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmLeadRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmNoteRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmOpportunityRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmTimelineEventRepository;

@ExtendWith(MockitoExtension.class)
class TimelineServiceDealNoteTest {

  @Mock private CrmNoteRepository noteRepository;
  @Mock private CrmTimelineEventRepository timelineRepository;
  @Mock private CrmLeadRepository leadRepository;
  @Mock private CrmAccountRepository accountRepository;
  @Mock private CrmOpportunityRepository opportunityRepository;

  private TimelineService service;

  @BeforeEach
  void setUp() {
    TenantContextFilter.bindTenantForTests("CRM-DEMO-01");
    service =
        new TimelineService(
            noteRepository, timelineRepository, leadRepository, accountRepository, opportunityRepository);
  }

  @AfterEach
  void tearDown() {
    TenantContextFilter.clearTenantForTests();
  }

  @Test
  void storesTheNoteOnTheDeal() {
    when(opportunityRepository.findByTenantIdAndIdAndDeletedAtIsNull("CRM-DEMO-01", 15L))
        .thenReturn(Optional.of(new CrmOpportunityEntity()));
    when(noteRepository.save(any()))
        .thenAnswer(
            invocation -> {
              CrmNoteEntity note = invocation.getArgument(0);
              note.setId(9L);
              return note;
            });
    when(timelineRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    NoteResponse saved = service.addOpportunityNote(15L, new NoteRequest("Price discussed"));

    assertThat(saved.relatedType()).isEqualTo("OPPORTUNITY");
    assertThat(saved.relatedId()).isEqualTo(15L);
    assertThat(saved.body()).isEqualTo("Price discussed");
  }

  @Test
  void rejectsABlankDealNote() {
    when(opportunityRepository.findByTenantIdAndIdAndDeletedAtIsNull("CRM-DEMO-01", 15L))
        .thenReturn(Optional.of(new CrmOpportunityEntity()));

    assertThatThrownBy(() -> service.addOpportunityNote(15L, new NoteRequest("  ")))
        .isInstanceOf(ResponseStatusException.class);
  }
}
