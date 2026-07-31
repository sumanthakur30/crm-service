package com.shopmanagement.crmservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
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

import com.shopmanagement.crmservice.filter.TenantContextFilter;
import com.shopmanagement.crmservice.persistence.entity.CrmCaseEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmCsatResponseEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmCaseRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmCsatResponseRepository;

@ExtendWith(MockitoExtension.class)
class CaseServiceTest {

  @Mock private CrmCaseRepository caseRepository;
  @Mock private CrmCsatResponseRepository csatResponseRepository;

  private CaseService service;

  @BeforeEach
  void setUp() {
    service = new CaseService(caseRepository, csatResponseRepository);
    TenantContextFilter.bindTenantForTests("demo-crm");
  }

  @AfterEach
  void tearDown() {
    TenantContextFilter.clearTenantForTests();
  }

  @Test
  void createPersistsOpenCase() {
    when(caseRepository.save(any(CrmCaseEntity.class)))
        .thenAnswer(
            inv -> {
              CrmCaseEntity e = inv.getArgument(0);
              e.setId(42L);
              return e;
            });

    Map<String, Object> result =
        service.create(
            Map.of(
                "subject", "Billing question",
                "priority", "HIGH",
                "relatedLeadId", 9));

    assertEquals(42L, result.get("id"));
    assertEquals("Billing question", result.get("subject"));
    assertEquals("OPEN", result.get("status"));
    assertEquals("HIGH", result.get("priority"));
    assertEquals(9L, result.get("relatedLeadId"));

    ArgumentCaptor<CrmCaseEntity> cap = ArgumentCaptor.forClass(CrmCaseEntity.class);
    verify(caseRepository).save(cap.capture());
    assertEquals("demo-crm", cap.getValue().getTenantId());
  }

  @Test
  void submitCsatUpdatesCaseAndAppendsResponse() {
    CrmCaseEntity existing = new CrmCaseEntity();
    existing.setId(7L);
    existing.setTenantId("demo-crm");
    existing.setSubject("Support");
    existing.setStatus("RESOLVED");
    existing.setPriority("MEDIUM");
    when(caseRepository.findByTenantIdAndIdAndDeletedAtIsNull("demo-crm", 7L))
        .thenReturn(Optional.of(existing));
    when(caseRepository.save(any(CrmCaseEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    when(csatResponseRepository.save(any(CrmCsatResponseEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    Map<String, Object> result = service.submitCsat(7L, Map.of("score", 5, "comment", "Great help"));

    assertEquals(5, result.get("csatScore"));
    assertEquals("Great help", result.get("csatComment"));
    assertNotNull(result.get("csatSubmittedAt"));

    ArgumentCaptor<CrmCsatResponseEntity> cap = ArgumentCaptor.forClass(CrmCsatResponseEntity.class);
    verify(csatResponseRepository).save(cap.capture());
    assertEquals(7L, cap.getValue().getCaseId());
    assertEquals(5, cap.getValue().getScore());
  }

  @Test
  void updateStatusChangesStatus() {
    CrmCaseEntity existing = new CrmCaseEntity();
    existing.setId(3L);
    existing.setTenantId("demo-crm");
    existing.setSubject("X");
    existing.setStatus("OPEN");
    existing.setPriority("LOW");
    when(caseRepository.findByTenantIdAndIdAndDeletedAtIsNull("demo-crm", 3L))
        .thenReturn(Optional.of(existing));
    when(caseRepository.save(any(CrmCaseEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    Map<String, Object> result = service.updateStatus(3L, Map.of("status", "CLOSED"));

    assertEquals("CLOSED", result.get("status"));
  }
}
