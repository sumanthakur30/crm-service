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

import com.shopmanagement.crmservice.persistence.entity.CrmAuditExportJobEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmLeadEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmTenantEnterpriseEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmAuditExportJobRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmInboundEventRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmLeadRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmTenantEnterpriseRepository;
import com.shopmanagement.crmservice.support.TenantIds;

@Service
public class EnterpriseGovernanceService {

  private static final Set<String> RESIDENCY = Set.of("IN", "EU", "US", "APAC", "GLOBAL");

  private final CrmTenantEnterpriseRepository enterpriseRepository;
  private final CrmAuditExportJobRepository auditExportJobRepository;
  private final CrmLeadRepository leadRepository;
  private final CrmInboundEventRepository inboundEventRepository;

  public EnterpriseGovernanceService(
      CrmTenantEnterpriseRepository enterpriseRepository,
      CrmAuditExportJobRepository auditExportJobRepository,
      CrmLeadRepository leadRepository,
      CrmInboundEventRepository inboundEventRepository) {
    this.enterpriseRepository = enterpriseRepository;
    this.auditExportJobRepository = auditExportJobRepository;
    this.leadRepository = leadRepository;
    this.inboundEventRepository = inboundEventRepository;
  }

  @Transactional
  public Map<String, Object> getOrCreateSettings() {
    return toSettings(ensure());
  }

  @Transactional
  public Map<String, Object> updateSettings(Map<String, Object> body) {
    CrmTenantEnterpriseEntity e = ensure();
    if (body.get("dataResidency") != null) {
      String r = String.valueOf(body.get("dataResidency")).trim().toUpperCase(Locale.ROOT);
      if (!RESIDENCY.contains(r)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid dataResidency");
      }
      e.setDataResidency(r);
    }
    if (body.get("ssoEnabled") != null) {
      e.setSsoEnabled(Boolean.TRUE.equals(body.get("ssoEnabled")) || "true".equalsIgnoreCase(String.valueOf(body.get("ssoEnabled"))));
    }
    if (body.containsKey("ssoProvider")) {
      e.setSsoProvider(blankToNull(body.get("ssoProvider")));
    }
    if (body.containsKey("ssoMetadataUrl")) {
      e.setSsoMetadataUrl(blankToNull(body.get("ssoMetadataUrl")));
    }
    if (body.get("auditExportEnabled") != null) {
      e.setAuditExportEnabled(Boolean.parseBoolean(String.valueOf(body.get("auditExportEnabled"))));
    }
    if (body.get("aiEnabled") != null) {
      e.setAiEnabled(Boolean.parseBoolean(String.valueOf(body.get("aiEnabled"))));
    }
    if (body.get("preferredLanguage") != null) {
      e.setPreferredLanguage(String.valueOf(body.get("preferredLanguage")).trim().toLowerCase(Locale.ROOT));
    }
    e.setUpdatedAt(Instant.now());
    return toSettings(enterpriseRepository.save(e));
  }

  @Transactional
  public Map<String, Object> ssoStatus() {
    CrmTenantEnterpriseEntity e = ensure();
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("ssoEnabled", e.isSsoEnabled());
    m.put("ssoProvider", e.getSsoProvider());
    m.put("ssoMetadataUrl", e.getSsoMetadataUrl());
    m.put(
        "note",
        e.isSsoEnabled()
            ? "SSO metadata registered — wire auth-service SAML/OIDC handshake in deploy"
            : "SSO disabled — enable and set provider + metadata URL");
    return m;
  }

  @Transactional
  public Map<String, Object> requestAuditExport(Map<String, Object> body) {
    CrmTenantEnterpriseEntity settings = ensure();
    if (!settings.isAuditExportEnabled()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Audit export disabled for tenant");
    }
    String tenantId = TenantIds.require();
    CrmAuditExportJobEntity job = new CrmAuditExportJobEntity();
    job.setTenantId(tenantId);
    job.setStatus("RUNNING");
    job.setFormat(
        body.get("format") == null ? "JSON" : String.valueOf(body.get("format")).toUpperCase(Locale.ROOT));
    job.setRequestedBy(TenantIds.currentUserOrNull());
    if (body.get("fromAt") != null) {
      job.setFromAt(Instant.parse(String.valueOf(body.get("fromAt"))));
    }
    if (body.get("toAt") != null) {
      job.setToAt(Instant.parse(String.valueOf(body.get("toAt"))));
    }
    job = auditExportJobRepository.save(job);

    try {
      List<Map<String, Object>> leads = new ArrayList<>();
      for (CrmLeadEntity lead : leadRepository.findByTenantIdAndDeletedAtIsNull(tenantId)) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", lead.getId());
        row.put("title", lead.getTitle());
        row.put("status", lead.getStatus());
        row.put("score", lead.getScore());
        row.put("sourceCode", lead.getSourceCode());
        row.put("utmSource", lead.getUtmSource());
        row.put("createdAt", lead.getCreatedAt());
        row.put("updatedAt", lead.getUpdatedAt());
        leads.add(row);
      }
      List<Map<String, Object>> inbound =
          inboundEventRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
              .limit(200)
              .map(
                  ev -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", ev.getId());
                    m.put("provider", ev.getProvider());
                    m.put("status", ev.getStatus());
                    m.put("leadId", ev.getLeadId());
                    m.put("createdAt", ev.getCreatedAt());
                    return m;
                  })
              .toList();

      Map<String, Object> result = new LinkedHashMap<>();
      result.put("tenantId", tenantId);
      result.put("dataResidency", settings.getDataResidency());
      result.put("exportedAt", Instant.now().toString());
      result.put("leads", leads);
      result.put("inboundEvents", inbound);
      job.setResultJson(result);
      job.setRowCount(leads.size() + inbound.size());
      job.setStatus("DONE");
      job.setArtifactUrl("/api/v1/crm/enterprise/audit-exports/" + job.getId());
      job.setCompletedAt(Instant.now());
      return toJob(auditExportJobRepository.save(job));
    } catch (RuntimeException ex) {
      job.setStatus("FAILED");
      job.setErrorMessage(
          ex.getMessage() == null
              ? "export failed"
              : ex.getMessage().substring(0, Math.min(500, ex.getMessage().length())));
      job.setCompletedAt(Instant.now());
      auditExportJobRepository.save(job);
      throw ex;
    }
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listAuditExports() {
    return auditExportJobRepository.findByTenantIdOrderByCreatedAtDesc(TenantIds.require()).stream()
        .map(EnterpriseGovernanceService::toJob)
        .toList();
  }

  @Transactional(readOnly = true)
  public Map<String, Object> getAuditExport(Long id) {
    CrmAuditExportJobEntity job =
        auditExportJobRepository
            .findById(id)
            .filter(j -> TenantIds.require().equals(j.getTenantId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Export not found"));
    return toJob(job);
  }

  private CrmTenantEnterpriseEntity ensure() {
    String tenantId = TenantIds.require();
    return enterpriseRepository
        .findById(tenantId)
        .orElseGet(
            () -> {
              CrmTenantEnterpriseEntity e = new CrmTenantEnterpriseEntity();
              e.setTenantId(tenantId);
              e.setDataResidency("IN");
              e.setAiEnabled(true);
              e.setAuditExportEnabled(true);
              e.setPreferredLanguage("en");
              return enterpriseRepository.save(e);
            });
  }

  private static Map<String, Object> toSettings(CrmTenantEnterpriseEntity e) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("tenantId", e.getTenantId());
    m.put("dataResidency", e.getDataResidency());
    m.put("ssoEnabled", e.isSsoEnabled());
    m.put("ssoProvider", e.getSsoProvider());
    m.put("ssoMetadataUrl", e.getSsoMetadataUrl());
    m.put("auditExportEnabled", e.isAuditExportEnabled());
    m.put("aiEnabled", e.isAiEnabled());
    m.put("preferredLanguage", e.getPreferredLanguage());
    return m;
  }

  private static Map<String, Object> toJob(CrmAuditExportJobEntity j) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", j.getId());
    m.put("status", j.getStatus());
    m.put("format", j.getFormat());
    m.put("rowCount", j.getRowCount());
    m.put("artifactUrl", j.getArtifactUrl());
    m.put("errorMessage", j.getErrorMessage());
    m.put("result", j.getResultJson());
    m.put("createdAt", j.getCreatedAt());
    m.put("completedAt", j.getCompletedAt());
    return m;
  }

  private static String blankToNull(Object v) {
    if (v == null) {
      return null;
    }
    String s = String.valueOf(v).trim();
    return s.isBlank() ? null : s;
  }
}
