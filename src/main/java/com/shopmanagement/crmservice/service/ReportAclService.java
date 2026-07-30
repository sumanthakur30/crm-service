package com.shopmanagement.crmservice.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.shopmanagement.crmservice.persistence.entity.CrmFieldAclEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmReportScheduleEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmFieldAclRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmReportScheduleRepository;
import com.shopmanagement.crmservice.support.TenantIds;

@Service
public class ReportAclService {

  private static final Set<String> FREQUENCIES = Set.of("HOURLY", "DAILY", "WEEKLY");
  private static final Set<String> REPORT_TYPES =
      Set.of("FUNNEL", "SOURCES", "CAMPAIGNS", "OVERDUE", "FORECAST");
  private static final Set<String> ACCESS = Set.of("READ", "WRITE", "MASK", "DENY");

  private final CrmReportScheduleRepository reportRepository;
  private final CrmFieldAclRepository fieldAclRepository;
  private final AnalyticsService analyticsService;
  private final OpsService opsService;

  public ReportAclService(
      CrmReportScheduleRepository reportRepository,
      CrmFieldAclRepository fieldAclRepository,
      AnalyticsService analyticsService,
      OpsService opsService) {
    this.reportRepository = reportRepository;
    this.fieldAclRepository = fieldAclRepository;
    this.analyticsService = analyticsService;
    this.opsService = opsService;
  }

  @Transactional
  public Map<String, Object> upsertSchedule(Map<String, Object> body) {
    String tenantId = TenantIds.require();
    String code = req(body, "code").toUpperCase(Locale.ROOT);
    CrmReportScheduleEntity s =
        reportRepository
            .findByTenantIdAndCodeAndDeletedAtIsNull(tenantId, code)
            .orElseGet(CrmReportScheduleEntity::new);
    if (s.getId() == null) {
      s.setTenantId(tenantId);
      s.setCode(code);
    }
    s.setName(req(body, "name"));
    String type = req(body, "reportType").toUpperCase(Locale.ROOT);
    if (!REPORT_TYPES.contains(type)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid reportType");
    }
    s.setReportType(type);
    String freq =
        body.get("frequency") == null
            ? "DAILY"
            : String.valueOf(body.get("frequency")).toUpperCase(Locale.ROOT);
    if (!FREQUENCIES.contains(freq)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid frequency");
    }
    s.setFrequency(freq);
    if (body.get("recipients") instanceof List<?> list) {
      s.setRecipientsJson(new ArrayList<>(list));
    } else if (s.getRecipientsJson() == null) {
      s.setRecipientsJson(new ArrayList<>());
    }
    s.setActive(body.get("active") == null || Boolean.TRUE.equals(body.get("active")));
    s.touch();
    return toSchedule(reportRepository.save(s));
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listSchedules() {
    return reportRepository.findByTenantIdAndDeletedAtIsNullOrderByNameAsc(TenantIds.require()).stream()
        .map(ReportAclService::toSchedule)
        .toList();
  }

  @Transactional
  public Map<String, Object> runDueReports() {
    String tenantId = TenantIds.require();
    int ran = 0;
    List<Map<String, Object>> results = new ArrayList<>();
    for (CrmReportScheduleEntity s :
        reportRepository.findByTenantIdAndActiveTrueAndDeletedAtIsNull(tenantId)) {
      Map<String, Object> snapshot = buildReport(s.getReportType());
      s.setLastRunAt(Instant.now());
      s.setLastResultJson(snapshot);
      s.touch();
      reportRepository.save(s);
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("code", s.getCode());
      row.put("reportType", s.getReportType());
      row.put("recipients", s.getRecipientsJson());
      row.put("snapshotKeys", snapshot.keySet());
      results.add(row);
      ran++;
    }
    return Map.of("ran", ran, "results", results);
  }

  @Transactional
  public Map<String, Object> upsertFieldAcl(Map<String, Object> body) {
    String tenantId = TenantIds.require();
    String role = req(body, "roleCode").toUpperCase(Locale.ROOT);
    String objectType = req(body, "objectType").toUpperCase(Locale.ROOT);
    String field = req(body, "fieldName");
    String access = req(body, "access").toUpperCase(Locale.ROOT);
    if (!ACCESS.contains(access)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid access");
    }
    CrmFieldAclEntity acl =
        fieldAclRepository
            .findByTenantIdAndRoleCodeAndObjectTypeAndFieldNameAndDeletedAtIsNull(
                tenantId, role, objectType, field)
            .orElseGet(CrmFieldAclEntity::new);
    if (acl.getId() == null) {
      acl.setTenantId(tenantId);
      acl.setRoleCode(role);
      acl.setObjectType(objectType);
      acl.setFieldName(field);
    }
    acl.setAccess(access);
    acl.touch();
    return toAcl(fieldAclRepository.save(acl));
  }

  @Transactional
  public List<Map<String, Object>> ensureDefaultAcl() {
    String tenantId = TenantIds.require();
    upsertFieldAcl(
        Map.of(
            "roleCode", "SALES_REP",
            "objectType", "LEAD",
            "fieldName", "phone",
            "access", "MASK"));
    upsertFieldAcl(
        Map.of(
            "roleCode", "SALES_REP",
            "objectType", "LEAD",
            "fieldName", "email",
            "access", "READ"));
    upsertFieldAcl(
        Map.of(
            "roleCode", "MANAGER",
            "objectType", "LEAD",
            "fieldName", "phone",
            "access", "WRITE"));
    return listAcl(null);
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listAcl(String roleCode) {
    String tenantId = TenantIds.require();
    List<CrmFieldAclEntity> list =
        roleCode == null || roleCode.isBlank()
            ? fieldAclRepository.findByTenantIdAndDeletedAtIsNull(tenantId)
            : fieldAclRepository.findByTenantIdAndRoleCodeAndDeletedAtIsNull(
                tenantId, roleCode.trim().toUpperCase(Locale.ROOT));
    return list.stream().map(ReportAclService::toAcl).toList();
  }

  /** Apply MASK/DENY for a role on a lead-like map (UI/API projection helper). */
  @Transactional(readOnly = true)
  public Map<String, Object> projectLeadFields(String roleCode, Map<String, Object> lead) {
    if (roleCode == null || roleCode.isBlank() || lead == null) {
      return lead;
    }
    Map<String, Object> out = new LinkedHashMap<>(lead);
    for (CrmFieldAclEntity acl :
        fieldAclRepository.findByTenantIdAndRoleCodeAndDeletedAtIsNull(
            TenantIds.require(), roleCode.toUpperCase(Locale.ROOT))) {
      if (!"LEAD".equalsIgnoreCase(acl.getObjectType())) {
        continue;
      }
      String field = acl.getFieldName();
      if ("DENY".equals(acl.getAccess())) {
        out.remove(field);
      } else if ("MASK".equals(acl.getAccess()) && out.get(field) != null) {
        out.put(field, mask(String.valueOf(out.get(field))));
      }
    }
    return out;
  }

  private Map<String, Object> buildReport(String reportType) {
    return switch (reportType) {
      case "FORECAST" -> opsService.forecast();
      case "FUNNEL", "SOURCES", "CAMPAIGNS", "OVERDUE" -> analyticsService.summary();
      default -> Map.of("error", "unknown report");
    };
  }

  private static String mask(String value) {
    if (value.length() <= 4) {
      return "****";
    }
    return value.substring(0, 2) + "****" + value.substring(value.length() - 2);
  }

  private static Map<String, Object> toSchedule(CrmReportScheduleEntity s) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", s.getId());
    m.put("code", s.getCode());
    m.put("name", s.getName());
    m.put("reportType", s.getReportType());
    m.put("frequency", s.getFrequency());
    m.put("recipients", s.getRecipientsJson());
    m.put("active", s.isActive());
    m.put("lastRunAt", s.getLastRunAt());
    return m;
  }

  private static Map<String, Object> toAcl(CrmFieldAclEntity a) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", a.getId());
    m.put("roleCode", a.getRoleCode());
    m.put("objectType", a.getObjectType());
    m.put("fieldName", a.getFieldName());
    m.put("access", a.getAccess());
    return m;
  }

  private static String req(Map<String, Object> body, String key) {
    Object v = body.get(key);
    if (v == null || String.valueOf(v).isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " required");
    }
    return String.valueOf(v).trim();
  }
}
