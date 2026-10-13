package com.shopmanagement.crmservice.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.shopmanagement.crmservice.persistence.entity.CrmDuplicateRuleEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmDuplicateRuleRepository;
import com.shopmanagement.crmservice.support.TenantIds;

@Service
public class DuplicateRuleService {

  private final CrmDuplicateRuleRepository ruleRepository;

  public DuplicateRuleService(CrmDuplicateRuleRepository ruleRepository) {
    this.ruleRepository = ruleRepository;
  }

  @Transactional
  public List<Map<String, Object>> listOrEnsure(String objectType) {
    String tenantId = TenantIds.require();
    String type = objectType == null || objectType.isBlank() ? "LEAD" : objectType.trim().toUpperCase(Locale.ROOT);
    ensureDefaults(tenantId, type);
    return ruleRepository
        .findByTenantIdAndObjectTypeAndDeletedAtIsNullOrderByWeightDescIdAsc(tenantId, type)
        .stream()
        .map(DuplicateRuleService::toMap)
        .toList();
  }

  @Transactional
  public Map<String, Object> upsert(Map<String, Object> body) {
    String tenantId = TenantIds.require();
    Long id = body.get("id") == null ? null : Long.valueOf(String.valueOf(body.get("id")));
    CrmDuplicateRuleEntity rule;
    if (id != null) {
      rule =
          ruleRepository
              .findByTenantIdAndIdAndDeletedAtIsNull(tenantId, id)
              .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rule not found"));
    } else {
      String code = String.valueOf(body.getOrDefault("code", "")).trim();
      if (code.isBlank()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "code is required");
      }
      rule =
          ruleRepository
              .findByTenantIdAndCodeAndDeletedAtIsNull(tenantId, code)
              .orElseGet(
                  () -> {
                    CrmDuplicateRuleEntity created = new CrmDuplicateRuleEntity();
                    created.setTenantId(tenantId);
                    created.setCode(code);
                    return created;
                  });
    }
    if (body.get("objectType") != null) {
      rule.setObjectType(String.valueOf(body.get("objectType")).trim().toUpperCase(Locale.ROOT));
    } else if (rule.getObjectType() == null) {
      rule.setObjectType("LEAD");
    }
    if (body.get("matchField") != null) {
      rule.setMatchField(String.valueOf(body.get("matchField")).trim().toUpperCase(Locale.ROOT));
    }
    if (body.get("normalizeMode") != null) {
      rule.setNormalizeMode(String.valueOf(body.get("normalizeMode")).trim().toUpperCase(Locale.ROOT));
    }
    if (body.get("enabled") != null) {
      rule.setEnabled(Boolean.parseBoolean(String.valueOf(body.get("enabled"))));
    }
    if (body.get("weight") != null) {
      rule.setWeight(Integer.parseInt(String.valueOf(body.get("weight"))));
    }
    if (rule.getMatchField() == null || rule.getMatchField().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "matchField is required");
    }
    rule.touch();
    return toMap(ruleRepository.save(rule));
  }

  @Transactional(readOnly = true)
  public List<CrmDuplicateRuleEntity> enabledRules(String objectType) {
    String tenantId = TenantIds.require();
    String type = objectType == null || objectType.isBlank() ? "LEAD" : objectType.trim().toUpperCase(Locale.ROOT);
    ensureDefaults(tenantId, type);
    List<CrmDuplicateRuleEntity> all =
        ruleRepository.findByTenantIdAndObjectTypeAndDeletedAtIsNullOrderByWeightDescIdAsc(tenantId, type);
    List<CrmDuplicateRuleEntity> enabled = new ArrayList<>();
    for (CrmDuplicateRuleEntity r : all) {
      if (r.isEnabled()) {
        enabled.add(r);
      }
    }
    return enabled;
  }

  public static String normalize(String raw, String mode) {
    if (raw == null) {
      return null;
    }
    String v = raw.trim();
    if (v.isEmpty()) {
      return null;
    }
    String m = mode == null ? "EXACT" : mode.trim().toUpperCase(Locale.ROOT);
    return switch (m) {
      case "LOWER" -> v.toLowerCase(Locale.ROOT);
      case "DIGITS_ONLY" -> {
        String digits = v.replaceAll("\\D+", "");
        yield digits.isEmpty() ? null : digits;
      }
      default -> v;
    };
  }

  private void ensureDefaults(String tenantId, String objectType) {
    if (ruleRepository.countByTenantIdAndObjectTypeAndDeletedAtIsNull(tenantId, objectType) > 0) {
      return;
    }
    seed(tenantId, objectType, "PHONE", "PHONE", "DIGITS_ONLY", true, 30);
    seed(tenantId, objectType, "EMAIL", "EMAIL", "LOWER", true, 20);
    seed(tenantId, objectType, "GSTIN", "GSTIN", "LOWER", false, 40);
  }

  private void seed(
      String tenantId,
      String objectType,
      String code,
      String matchField,
      String normalizeMode,
      boolean enabled,
      int weight) {
    CrmDuplicateRuleEntity r = new CrmDuplicateRuleEntity();
    r.setTenantId(tenantId);
    r.setCode(code);
    r.setObjectType(objectType);
    r.setMatchField(matchField);
    r.setNormalizeMode(normalizeMode);
    r.setEnabled(enabled);
    r.setWeight(weight);
    ruleRepository.save(r);
  }

  private static Map<String, Object> toMap(CrmDuplicateRuleEntity r) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", r.getId());
    m.put("code", r.getCode());
    m.put("objectType", r.getObjectType());
    m.put("matchField", r.getMatchField());
    m.put("normalizeMode", r.getNormalizeMode());
    m.put("enabled", r.isEnabled());
    m.put("weight", r.getWeight());
    return m;
  }
}
