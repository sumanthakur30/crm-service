package com.shopmanagement.crmservice.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.shopmanagement.crmservice.persistence.entity.CrmCloseReasonEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmCloseReasonRepository;
import com.shopmanagement.crmservice.support.TenantIds;

@Service
public class CloseReasonService {

  private static final List<Seed> DEFAULTS =
      List.of(
          new Seed("WON_VALUE", "Won on value / ROI", "WON", 10),
          new Seed("WON_RELATIONSHIP", "Won on relationship", "WON", 20),
          new Seed("WON_PRODUCT_FIT", "Won on product fit", "WON", 30),
          new Seed("LOST_PRICE", "Lost to price", "LOST", 10),
          new Seed("LOST_COMPETITOR", "Lost to competitor", "LOST", 20),
          new Seed("LOST_NO_BUDGET", "No budget / deferred", "LOST", 30),
          new Seed("LOST_TIMING", "Bad timing", "LOST", 40),
          new Seed("LOST_FEATURE_GAP", "Feature gap", "LOST", 50),
          new Seed("LOST_NO_DECISION", "No decision / ghosted", "LOST", 60));

  private final CrmCloseReasonRepository repository;

  public CloseReasonService(CrmCloseReasonRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public List<Map<String, Object>> list(String outcome) {
    String tenantId = TenantIds.require();
    ensureDefaults(tenantId);
    String filter =
        outcome == null || outcome.isBlank() ? null : outcome.trim().toUpperCase(Locale.ROOT);
    return repository
        .findByTenantIdAndDeletedAtIsNullAndActiveTrueOrderBySortOrderAscNameAsc(tenantId)
        .stream()
        .filter(r -> filter == null || "BOTH".equals(r.getOutcome()) || filter.equals(r.getOutcome()))
        .map(CloseReasonService::toMap)
        .toList();
  }

  @Transactional
  public CrmCloseReasonEntity requireValid(String tenantId, String code, String requiredOutcome) {
    ensureDefaults(tenantId);
    if (code == null || code.isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "closeReasonCode is required for " + requiredOutcome + " deals");
    }
    String normalized = code.trim().toUpperCase(Locale.ROOT);
    CrmCloseReasonEntity reason =
        repository
            .findByTenantIdAndCodeAndDeletedAtIsNull(tenantId, normalized)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown closeReasonCode: " + code));
    if (!reason.isActive()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "closeReasonCode is inactive: " + code);
    }
    String outcome = reason.getOutcome() == null ? "" : reason.getOutcome().toUpperCase(Locale.ROOT);
    if (!"BOTH".equals(outcome) && !requiredOutcome.equalsIgnoreCase(outcome)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "closeReasonCode " + code + " is not valid for " + requiredOutcome);
    }
    return reason;
  }

  private void ensureDefaults(String tenantId) {
    if (repository.countByTenantIdAndDeletedAtIsNull(tenantId) > 0) {
      return;
    }
    for (Seed seed : DEFAULTS) {
      CrmCloseReasonEntity e = new CrmCloseReasonEntity();
      e.setTenantId(tenantId);
      e.setCode(seed.code());
      e.setName(seed.name());
      e.setOutcome(seed.outcome());
      e.setSortOrder(seed.sortOrder());
      e.setActive(true);
      repository.save(e);
    }
  }

  private static Map<String, Object> toMap(CrmCloseReasonEntity e) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", e.getId());
    m.put("code", e.getCode());
    m.put("name", e.getName());
    m.put("outcome", e.getOutcome());
    m.put("sortOrder", e.getSortOrder());
    m.put("active", e.isActive());
    return m;
  }

  private record Seed(String code, String name, String outcome, int sortOrder) {}
}
