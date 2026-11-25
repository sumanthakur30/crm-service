package com.shopmanagement.crmservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shopmanagement.crmservice.config.CrmNotificationProperties;
import com.shopmanagement.crmservice.filter.TenantContextFilter;
import com.shopmanagement.crmservice.integration.NotificationClient;
import com.shopmanagement.crmservice.persistence.entity.CrmSequenceEnrollmentEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmSequenceStepEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmLeadRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmOpportunityRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmSequenceEnrollmentRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmSequenceRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmSequenceStepRepository;

@ExtendWith(MockitoExtension.class)
class SequenceServiceTest {

  @Mock private CrmSequenceRepository sequenceRepository;
  @Mock private CrmSequenceStepRepository stepRepository;
  @Mock private CrmSequenceEnrollmentRepository enrollmentRepository;
  @Mock private CrmLeadRepository leadRepository;
  @Mock private CrmOpportunityRepository opportunityRepository;
  @Mock private NotificationClient notificationClient;
  @Mock private TimelineService timelineService;
  @Mock private CrmNotificationProperties notificationProperties;

  private SequenceService service;

  @BeforeEach
  void setUp() {
    TenantContextFilter.bindTenantForTests("t1");
    service =
        new SequenceService(
            sequenceRepository,
            stepRepository,
            enrollmentRepository,
            leadRepository,
            opportunityRepository,
            notificationClient,
            timelineService);
  }

  @AfterEach
  void tearDown() {
    TenantContextFilter.clearTenantForTests();
  }

  @Test
  void skippedSendDoesNotAdvanceTheStep() {
    CrmSequenceEnrollmentEntity enrollment = new CrmSequenceEnrollmentEntity();
    enrollment.setId(4L);
    enrollment.setTenantId("t1");
    enrollment.setSequenceId(8L);
    enrollment.setLeadId(3L);
    enrollment.setStatus("ACTIVE");
    enrollment.setCurrentStep(0);
    enrollment.setChannel("WHATSAPP");
    enrollment.setRecipient("9999999999");

    CrmSequenceStepEntity step = new CrmSequenceStepEntity();
    step.setChannel("WHATSAPP");
    step.setBodyTemplate("Hello {{name}}");
    step.setDelayHours(0);

    when(enrollmentRepository
            .findByTenantIdAndStatusAndNextRunAtLessThanEqualAndDeletedAtIsNullOrderByNextRunAtAsc(
                anyString(), anyString(), any()))
        .thenReturn(List.of(enrollment));
    when(stepRepository.findByTenantIdAndSequenceIdAndDeletedAtIsNullOrderBySortOrderAsc(anyString(), any()))
        .thenReturn(List.of(step));
    when(notificationClient.properties()).thenReturn(notificationProperties);
    when(notificationProperties.getSequenceStepTemplate()).thenReturn("CRM_SEQ_STEP");
    when(notificationClient.queue(
            anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), any(), any()))
        .thenReturn(Map.of("status", "SKIPPED_DISABLED"));

    var result = service.processDue(10);

    assertThat(result.processed()).isEqualTo(1);
    assertThat(enrollment.getCurrentStep()).isZero();
    assertThat(enrollment.getStatus()).isEqualTo("FAILED");
    assertThat(enrollment.getLastError()).isEqualTo("SKIPPED_DISABLED");
    verify(enrollmentRepository).save(enrollment);
  }
}
