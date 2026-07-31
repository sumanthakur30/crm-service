package com.shopmanagement.crmservice.meter;

import java.time.Instant;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.shopmanagement.crmservice.persistence.entity.CrmUsageCounterEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmUsageCounterRepository;
import com.shopmanagement.crmservice.service.UsageMeterService;

/** Durable Postgres API call counter (default + Redis fail-soft fallback). */
@Component
public class DbApiCallCounter implements ApiCallCounter {

  private final CrmUsageCounterRepository counterRepository;

  public DbApiCallCounter(CrmUsageCounterRepository counterRepository) {
    this.counterRepository = counterRepository;
  }

  @Override
  @Transactional
  public long increment(String tenantId, String periodKey) {
    CrmUsageCounterEntity row = loadOrCreate(tenantId, periodKey);
    long next = row.getUsedCount() + 1;
    row.setUsedCount(next);
    row.setUpdatedAt(Instant.now());
    counterRepository.save(row);
    return next;
  }

  @Override
  @Transactional(readOnly = true)
  public long get(String tenantId, String periodKey) {
    return counterRepository.findById(pk(tenantId, periodKey)).map(CrmUsageCounterEntity::getUsedCount).orElse(0L);
  }

  private CrmUsageCounterEntity loadOrCreate(String tenantId, String periodKey) {
    CrmUsageCounterEntity.Pk id = pk(tenantId, periodKey);
    return counterRepository
        .findById(id)
        .orElseGet(
            () -> {
              CrmUsageCounterEntity e = new CrmUsageCounterEntity();
              e.setId(id);
              e.setUsedCount(0);
              e.setUpdatedAt(Instant.now());
              return e;
            });
  }

  private static CrmUsageCounterEntity.Pk pk(String tenantId, String periodKey) {
    CrmUsageCounterEntity.Pk pk = new CrmUsageCounterEntity.Pk();
    pk.setTenantId(tenantId);
    pk.setMeterCode(UsageMeterService.METER_API);
    pk.setPeriodKey(periodKey);
    return pk;
  }
}
