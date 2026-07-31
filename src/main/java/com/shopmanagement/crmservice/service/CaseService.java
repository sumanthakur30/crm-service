package com.shopmanagement.crmservice.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.shopmanagement.crmservice.persistence.entity.CrmCaseEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmCsatResponseEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmCaseRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmCsatResponseRepository;
import com.shopmanagement.crmservice.support.TenantIds;

@Service
public class CaseService {

  private static final Set<String> STATUSES = Set.of("OPEN", "PENDING", "RESOLVED", "CLOSED");
  private static final Set<String> PRIORITIES = Set.of("LOW", "MEDIUM", "HIGH", "URGENT");

  private final CrmCaseRepository caseRepository;
  private final CrmCsatResponseRepository csatResponseRepository;

  public CaseService(CrmCaseRepository caseRepository, CrmCsatResponseRepository csatResponseRepository) {
    this.caseRepository = caseRepository;
    this.csatResponseRepository = csatResponseRepository;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> list(String status) {
    String tenantId = TenantIds.require();
    List<CrmCaseEntity> rows;
    if (status != null && !status.isBlank()) {
      rows =
          caseRepository.findByTenantIdAndStatusAndDeletedAtIsNullOrderByUpdatedAtDesc(
              tenantId, normalizeStatus(status));
    } else {
      rows = caseRepository.findByTenantIdAndDeletedAtIsNullOrderByUpdatedAtDesc(tenantId);
    }
    return rows.stream().map(CaseService::toMap).toList();
  }

  @Transactional(readOnly = true)
  public Map<String, Object> get(Long id) {
    return toMap(require(id));
  }

  @Transactional
  public Map<String, Object> create(Map<String, Object> body) {
    String tenantId = TenantIds.require();
    CrmCaseEntity c = new CrmCaseEntity();
    c.setTenantId(tenantId);
    c.setSubject(req(body, "subject"));
    c.setStatus(optionalStatus(body.get("status"), "OPEN"));
    c.setPriority(optionalPriority(body.get("priority"), "MEDIUM"));
    c.setRelatedLeadId(longOrNull(body.get("relatedLeadId")));
    c.setRelatedOpportunityId(longOrNull(body.get("relatedOpportunityId")));
    c.setAssignedTo(blankToNull(str(body.get("assignedTo"))));
    return toMap(caseRepository.save(c));
  }

  @Transactional
  public Map<String, Object> updateStatus(Long id, Map<String, Object> body) {
    CrmCaseEntity c = require(id);
    c.setStatus(normalizeStatus(req(body, "status")));
    if (body.containsKey("assignedTo")) {
      c.setAssignedTo(blankToNull(str(body.get("assignedTo"))));
    }
    if (body.containsKey("priority")) {
      c.setPriority(normalizePriority(str(body.get("priority"))));
    }
    c.touch();
    return toMap(caseRepository.save(c));
  }

  @Transactional
  public Map<String, Object> submitCsat(Long id, Map<String, Object> body) {
    CrmCaseEntity c = require(id);
    int score = intReq(body, "score");
    if (score < 1 || score > 5) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "score must be 1-5");
    }
    String comment = blankToNull(str(body.get("comment")));
    if (comment == null) {
      comment = blankToNull(str(body.get("csatComment")));
    }

    Instant now = Instant.now();
    c.setCsatScore(score);
    c.setCsatComment(comment);
    c.setCsatSubmittedAt(now);
    c.touch();
    caseRepository.save(c);

    CrmCsatResponseEntity resp = new CrmCsatResponseEntity();
    resp.setTenantId(c.getTenantId());
    resp.setCaseId(c.getId());
    resp.setScore(score);
    resp.setComment(comment);
    resp.setSubmittedBy(
        blankToNull(str(body.get("submittedBy"))) != null
            ? blankToNull(str(body.get("submittedBy")))
            : TenantIds.currentUserOrNull());
    resp.setCreatedAt(now);
    csatResponseRepository.save(resp);

    return toMap(c);
  }

  private CrmCaseEntity require(Long id) {
    String tenantId = TenantIds.require();
    return caseRepository
        .findByTenantIdAndIdAndDeletedAtIsNull(tenantId, id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found"));
  }

  private static Map<String, Object> toMap(CrmCaseEntity c) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", c.getId());
    m.put("subject", c.getSubject());
    m.put("status", c.getStatus());
    m.put("priority", c.getPriority());
    m.put("relatedLeadId", c.getRelatedLeadId());
    m.put("relatedOpportunityId", c.getRelatedOpportunityId());
    m.put("assignedTo", c.getAssignedTo());
    m.put("csatScore", c.getCsatScore());
    m.put("csatComment", c.getCsatComment());
    m.put("csatSubmittedAt", c.getCsatSubmittedAt() == null ? null : c.getCsatSubmittedAt().toString());
    m.put("createdAt", c.getCreatedAt() == null ? null : c.getCreatedAt().toString());
    m.put("updatedAt", c.getUpdatedAt() == null ? null : c.getUpdatedAt().toString());
    return m;
  }

  private static String optionalStatus(Object raw, String fallback) {
    String v = str(raw);
    if (v == null || v.isBlank()) {
      return fallback;
    }
    return normalizeStatus(v);
  }

  private static String optionalPriority(Object raw, String fallback) {
    String v = str(raw);
    if (v == null || v.isBlank()) {
      return fallback;
    }
    return normalizePriority(v);
  }

  private static String normalizeStatus(String status) {
    String s = status.trim().toUpperCase(Locale.ROOT);
    if (!STATUSES.contains(s)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "status must be one of OPEN, PENDING, RESOLVED, CLOSED");
    }
    return s;
  }

  private static String normalizePriority(String priority) {
    String p = priority.trim().toUpperCase(Locale.ROOT);
    if (!PRIORITIES.contains(p)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "priority must be one of LOW, MEDIUM, HIGH, URGENT");
    }
    return p;
  }

  private static String req(Map<String, Object> body, String key) {
    String v = str(body.get(key));
    if (v == null || v.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " is required");
    }
    return v.trim();
  }

  private static int intReq(Map<String, Object> body, String key) {
    Object raw = body.get(key);
    if (raw == null && body.get("csatScore") != null) {
      raw = body.get("csatScore");
    }
    if (raw instanceof Number n) {
      return n.intValue();
    }
    if (raw == null || String.valueOf(raw).isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " is required");
    }
    try {
      return Integer.parseInt(String.valueOf(raw).trim());
    } catch (NumberFormatException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " must be a number");
    }
  }

  private static Long longOrNull(Object raw) {
    if (raw == null || String.valueOf(raw).isBlank()) {
      return null;
    }
    if (raw instanceof Number n) {
      return n.longValue();
    }
    try {
      return Long.parseLong(String.valueOf(raw).trim());
    } catch (NumberFormatException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid id value");
    }
  }

  private static String str(Object v) {
    return v == null ? null : String.valueOf(v);
  }

  private static String blankToNull(String v) {
    return v == null || v.isBlank() ? null : v.trim();
  }
}
