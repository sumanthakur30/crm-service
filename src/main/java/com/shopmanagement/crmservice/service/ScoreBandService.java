package com.shopmanagement.crmservice.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.shopmanagement.crmservice.persistence.entity.CrmScoreBandEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmScoreBandRepository;
import com.shopmanagement.crmservice.support.TenantIds;

/** Tenant Hot / Warm / Cold thresholds derived from lead score. */
@Service
public class ScoreBandService {

  public static final int DEFAULT_HOT_MIN = 70;
  public static final int DEFAULT_WARM_MIN = 40;

  private final CrmScoreBandRepository bandRepository;

  public ScoreBandService(CrmScoreBandRepository bandRepository) {
    this.bandRepository = bandRepository;
  }

  /** Ensures default bands exist; REQUIRES_NEW so callers in readOnly txs (e.g. lead search) can seed. */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Map<String, Object> getOrEnsure() {
    return toMap(ensureEntity(TenantIds.require()));
  }

  @Transactional
  public Map<String, Object> update(Map<String, Object> body) {
    String tenantId = TenantIds.require();
    CrmScoreBandEntity band = ensureEntity(tenantId);
    int hotMin = body.get("hotMin") != null ? intVal(body.get("hotMin")) : band.getHotMin();
    int warmMin = body.get("warmMin") != null ? intVal(body.get("warmMin")) : band.getWarmMin();
    if (warmMin < 0 || hotMin > 100 || hotMin < warmMin) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Require 0 <= warmMin <= hotMin <= 100");
    }
    band.setHotMin(hotMin);
    band.setWarmMin(warmMin);
    band.touch();
    return toMap(bandRepository.save(band));
  }

  @Transactional(readOnly = true)
  public int hotMin() {
    return bandRepository
        .findByTenantIdAndDeletedAtIsNull(TenantIds.require())
        .map(CrmScoreBandEntity::getHotMin)
        .orElse(DEFAULT_HOT_MIN);
  }

  /** HOT if score >= hotMin; WARM if score >= warmMin; else COLD. */
  @Transactional(readOnly = true)
  public String resolveBand(int score) {
    CrmScoreBandEntity band =
        bandRepository.findByTenantIdAndDeletedAtIsNull(TenantIds.require()).orElse(null);
    int hot = band != null ? band.getHotMin() : DEFAULT_HOT_MIN;
    int warm = band != null ? band.getWarmMin() : DEFAULT_WARM_MIN;
    return resolveBand(score, hot, warm);
  }

  public static String resolveBand(int score, int hotMin, int warmMin) {
    if (score >= hotMin) {
      return "HOT";
    }
    if (score >= warmMin) {
      return "WARM";
    }
    return "COLD";
  }

  private CrmScoreBandEntity ensureEntity(String tenantId) {
    return bandRepository
        .findByTenantIdAndDeletedAtIsNull(tenantId)
        .orElseGet(
            () -> {
              CrmScoreBandEntity created = new CrmScoreBandEntity();
              created.setTenantId(tenantId);
              created.setHotMin(DEFAULT_HOT_MIN);
              created.setWarmMin(DEFAULT_WARM_MIN);
              return bandRepository.save(created);
            });
  }

  private static int intVal(Object raw) {
    return Integer.parseInt(String.valueOf(raw));
  }

  private static Map<String, Object> toMap(CrmScoreBandEntity b) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", b.getId());
    m.put("hotMin", b.getHotMin());
    m.put("warmMin", b.getWarmMin());
    m.put(
        "legend",
        Map.of(
            "HOT", "score >= " + b.getHotMin(),
            "WARM", b.getWarmMin() + " <= score < " + b.getHotMin(),
            "COLD", "score < " + b.getWarmMin()));
    return m;
  }
}
