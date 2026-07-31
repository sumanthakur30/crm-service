package com.shopmanagement.crmservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shopmanagement.crmservice.config.CrmProperties;
import com.shopmanagement.crmservice.entitlement.SubscriptionEntitlementClient;
import com.shopmanagement.crmservice.filter.TenantContextFilter;
import com.shopmanagement.crmservice.meter.CrmMeterExceededException;
import com.shopmanagement.crmservice.persistence.entity.CrmUsageCounterEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmTeamMemberRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmUsageCounterRepository;

@ExtendWith(MockitoExtension.class)
class UsageMeterServiceTest {

  @Mock private CrmUsageCounterRepository counterRepository;
  @Mock private CrmTeamMemberRepository teamMemberRepository;
  @Mock private SubscriptionEntitlementClient entitlementClient;

  private CrmProperties properties;
  private UsageMeterService service;

  @BeforeEach
  void setUp() {
    properties = new CrmProperties();
    properties.setEnabled(true);
    properties.setFailOpen(true);
    service =
        new UsageMeterService(
            counterRepository, teamMemberRepository, entitlementClient, properties);
    TenantContextFilter.bindTenantForTests("demo-crm");
  }

  @AfterEach
  void tearDown() {
    TenantContextFilter.clearTenantForTests();
  }

  @Test
  void incrementTracksAndAllowsUnderLimit() {
    when(counterRepository.findById(any())).thenReturn(Optional.empty());
    when(counterRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(entitlementClient.limitOrNull(eq("demo-crm"), eq(UsageMeterService.LIMIT_API)))
        .thenReturn(100L);

    long used = service.incrementApiCall("demo-crm");
    assertEquals(1L, used);
  }

  @Test
  void incrementThrows429StyleWhenOverLimit() {
    CrmUsageCounterEntity existing = new CrmUsageCounterEntity();
    CrmUsageCounterEntity.Pk pk = new CrmUsageCounterEntity.Pk();
    pk.setTenantId("demo-crm");
    pk.setMeterCode(UsageMeterService.METER_API);
    pk.setPeriodKey(UsageMeterService.currentMonthPeriod());
    existing.setId(pk);
    existing.setUsedCount(5);

    when(counterRepository.findById(any())).thenReturn(Optional.of(existing));
    when(counterRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(entitlementClient.limitOrNull(eq("demo-crm"), eq(UsageMeterService.LIMIT_API)))
        .thenReturn(5L);

    CrmMeterExceededException ex =
        assertThrows(CrmMeterExceededException.class, () -> service.incrementApiCall("demo-crm"));
    assertEquals("CRM_API_METER_EXCEEDED", ex.getCode());
  }

  @Test
  void noEnforceWhenEntitlementsDisabled() {
    properties.setEnabled(false);
    CrmUsageCounterEntity existing = new CrmUsageCounterEntity();
    CrmUsageCounterEntity.Pk pk = new CrmUsageCounterEntity.Pk();
    pk.setTenantId("demo-crm");
    pk.setMeterCode(UsageMeterService.METER_API);
    pk.setPeriodKey(UsageMeterService.currentMonthPeriod());
    existing.setId(pk);
    existing.setUsedCount(9999);
    when(counterRepository.findById(any())).thenReturn(Optional.of(existing));
    when(counterRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    assertEquals(10000L, service.incrementApiCall("demo-crm"));
  }
}
