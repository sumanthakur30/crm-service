package com.shopmanagement.crmservice.service;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopmanagement.crmservice.config.CrmProperties;
import com.shopmanagement.crmservice.entitlement.SubscriptionEntitlementClient;
import com.shopmanagement.crmservice.meter.CrmMeterExceededException;
import com.shopmanagement.crmservice.persistence.entity.CrmUsageCounterEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmTeamMemberRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmUsageCounterRepository;
import com.shopmanagement.crmservice.support.TenantIds;

@Service
public class UsageMeterService {

  public static final String METER_SEATS = "SEATS";
  public static final String METER_API = "API_CALLS_MONTH";
  public static final String PERIOD_ALL = "ALL";
  public static final String LIMIT_SEATS = "crm.max_users";
  public static final String LIMIT_API = "crm.max_api_calls_month";

  private final CrmUsageCounterRepository counterRepository;
  private final CrmTeamMemberRepository teamMemberRepository;
  private final SubscriptionEntitlementClient entitlementClient;
  private final CrmProperties entitlementProperties;

  public UsageMeterService(
      CrmUsageCounterRepository counterRepository,
      CrmTeamMemberRepository teamMemberRepository,
      SubscriptionEntitlementClient entitlementClient,
      CrmProperties entitlementProperties) {
    this.counterRepository = counterRepository;
    this.teamMemberRepository = teamMemberRepository;
    this.entitlementClient = entitlementClient;
    this.entitlementProperties = entitlementProperties;
  }

  @Transactional
  public long incrementApiCall(String tenantId) {
    String period = currentMonthPeriod();
    CrmUsageCounterEntity row = loadOrCreate(tenantId, METER_API, period);
    long next = row.getUsedCount() + 1;
    row.setUsedCount(next);
    row.setUpdatedAt(Instant.now());
    counterRepository.save(row);

    if (entitlementProperties.isEnabled()) {
      Long limit = safeLimit(tenantId, LIMIT_API);
      if (limit != null && limit >= 0 && next > limit) {
        throw new CrmMeterExceededException(
            "CRM_API_METER_EXCEEDED",
            "API call meter exceeded for period " + period + " (limit " + limit + ")");
      }
    }
    return next;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> snapshot() {
    String tenantId = TenantIds.require();
    String period = currentMonthPeriod();
    long apiUsed = used(tenantId, METER_API, period);
    long seatsUsed = used(tenantId, METER_SEATS, PERIOD_ALL);
    if (seatsUsed == 0) {
      seatsUsed = teamMemberRepository.countByTenantIdAndActiveTrueAndDeletedAtIsNull(tenantId);
    }
    Long seatsLimit = entitlementProperties.isEnabled() ? safeLimit(tenantId, LIMIT_SEATS) : null;
    Long apiLimit = entitlementProperties.isEnabled() ? safeLimit(tenantId, LIMIT_API) : null;

    Map<String, Object> seats = new LinkedHashMap<>();
    seats.put("used", seatsUsed);
    seats.put("limit", seatsLimit);

    Map<String, Object> api = new LinkedHashMap<>();
    api.put("used", apiUsed);
    api.put("limit", apiLimit);
    api.put("period", period);

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("seats", seats);
    out.put("apiCallsMonth", api);
    out.put("enforcementEnabled", entitlementProperties.isEnabled());
    return out;
  }

  @Transactional
  public Map<String, Object> setSeats(long usedCount) {
    if (usedCount < 0) {
      throw new IllegalStateException("usedCount must be >= 0");
    }
    String tenantId = TenantIds.require();
    enforceSeatLimit(tenantId, usedCount);
    CrmUsageCounterEntity row = loadOrCreate(tenantId, METER_SEATS, PERIOD_ALL);
    row.setUsedCount(usedCount);
    row.setUpdatedAt(Instant.now());
    counterRepository.save(row);
    return snapshot();
  }

  @Transactional(readOnly = true)
  public void assertCanAddSeat() {
    String tenantId = TenantIds.require();
    long current = used(tenantId, METER_SEATS, PERIOD_ALL);
    if (current == 0) {
      current = teamMemberRepository.countByTenantIdAndActiveTrueAndDeletedAtIsNull(tenantId);
    }
    enforceSeatLimit(tenantId, current + 1);
  }

  private void enforceSeatLimit(String tenantId, long projectedUsed) {
    if (!entitlementProperties.isEnabled()) {
      return;
    }
    Long limit = safeLimit(tenantId, LIMIT_SEATS);
    if (limit != null && limit >= 0 && projectedUsed > limit) {
      throw new CrmMeterExceededException(
          "CRM_SEAT_METER_EXCEEDED", "Seat meter exceeded (limit " + limit + ")");
    }
  }

  private Long safeLimit(String tenantId, String code) {
    try {
      return entitlementClient.limitOrNull(tenantId, code);
    } catch (RuntimeException ex) {
      if (entitlementProperties.isFailOpen()) {
        return null;
      }
      throw ex;
    }
  }

  private long used(String tenantId, String meter, String period) {
    CrmUsageCounterEntity.Pk pk = new CrmUsageCounterEntity.Pk();
    pk.setTenantId(tenantId);
    pk.setMeterCode(meter);
    pk.setPeriodKey(period);
    return counterRepository.findById(pk).map(CrmUsageCounterEntity::getUsedCount).orElse(0L);
  }

  private CrmUsageCounterEntity loadOrCreate(String tenantId, String meter, String period) {
    CrmUsageCounterEntity.Pk pk = new CrmUsageCounterEntity.Pk();
    pk.setTenantId(tenantId);
    pk.setMeterCode(meter);
    pk.setPeriodKey(period);
    return counterRepository
        .findById(pk)
        .orElseGet(
            () -> {
              CrmUsageCounterEntity e = new CrmUsageCounterEntity();
              e.setId(pk);
              e.setUsedCount(0);
              e.setUpdatedAt(Instant.now());
              return e;
            });
  }

  public static String currentMonthPeriod() {
    return YearMonth.now(ZoneOffset.UTC).toString();
  }
}
