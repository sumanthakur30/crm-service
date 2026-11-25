package com.shopmanagement.crmservice.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.shopmanagement.crmservice.api.CrmFormApi.FieldMapInput;
import com.shopmanagement.crmservice.api.CrmFormApi.FieldMapResponse;
import com.shopmanagement.crmservice.api.CrmFormApi.WebFormResponse;
import com.shopmanagement.crmservice.api.CrmFormApi.WebFormUpsert;
import com.shopmanagement.crmservice.persistence.entity.CrmFieldMapEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmWebFormEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmBranchRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmCampaignRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmFieldMapRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmWebFormRepository;
import com.shopmanagement.crmservice.support.TenantIds;

@Service
public class WebFormService {

  static final Set<String> TARGETS =
      Set.of("CONTACT_PHONE", "CONTACT_EMAIL", "CONTACT_NAME", "LEAD_TITLE", "COURSE", "STORE_ONLY");

  private final CrmWebFormRepository formRepository;
  private final CrmFieldMapRepository fieldMapRepository;
  private final CrmBranchRepository branchRepository;
  private final CrmCampaignRepository campaignRepository;

  public WebFormService(
      CrmWebFormRepository formRepository,
      CrmFieldMapRepository fieldMapRepository,
      CrmBranchRepository branchRepository,
      CrmCampaignRepository campaignRepository) {
    this.formRepository = formRepository;
    this.fieldMapRepository = fieldMapRepository;
    this.branchRepository = branchRepository;
    this.campaignRepository = campaignRepository;
  }

  @Transactional(readOnly = true)
  public List<WebFormResponse> list() {
    String tenantId = TenantIds.require();
    return formRepository.findByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc(tenantId).stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional
  public WebFormResponse create(WebFormUpsert body) {
    String tenantId = TenantIds.require();
    if (body == null || body.name() == null || body.name().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Form name is required");
    }
    validateLinks(tenantId, body.branchId(), body.campaignId());
    List<FieldMapInput> fields = normalizeFields(body.fields());
    CrmWebFormEntity form = new CrmWebFormEntity();
    form.setTenantId(tenantId);
    form.setName(body.name().trim());
    form.setPublicKey("wf_" + UUID.randomUUID().toString().replace("-", ""));
    form.setBranchId(body.branchId());
    form.setCampaignId(body.campaignId());
    form.setDefaultCourse(blank(body.defaultCourse()));
    form.setStatus("TEST");
    form.setHoneypotField(honeypot(body.honeypotField()));
    form = formRepository.save(form);
    saveFields(tenantId, form.getId(), fields);
    return toResponse(form);
  }

  @Transactional
  public WebFormResponse activate(Long id) {
    CrmWebFormEntity form = require(id);
    form.setStatus("ACTIVE");
    form.touch();
    return toResponse(formRepository.save(form));
  }

  private CrmWebFormEntity require(Long id) {
    return formRepository
        .findByTenantIdAndIdAndDeletedAtIsNull(TenantIds.require(), id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Form not found"));
  }

  private void validateLinks(String tenantId, Long branchId, Long campaignId) {
    if (branchId != null
        && branchRepository.findByTenantIdAndIdAndDeletedAtIsNull(tenantId, branchId).isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid branch");
    }
    if (campaignId != null
        && campaignRepository.findByTenantIdAndIdAndDeletedAtIsNull(tenantId, campaignId).isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid campaign");
    }
  }

  private void saveFields(String tenantId, Long formId, List<FieldMapInput> fields) {
    for (FieldMapInput field : fields) {
      CrmFieldMapEntity row = new CrmFieldMapEntity();
      row.setTenantId(tenantId);
      row.setFormId(formId);
      row.setExternalKey(field.externalKey().trim());
      row.setTarget(field.target().trim().toUpperCase(Locale.ROOT));
      row.setRequired(Boolean.TRUE.equals(field.required()));
      fieldMapRepository.save(row);
    }
  }

  private static List<FieldMapInput> normalizeFields(List<FieldMapInput> fields) {
    if (fields == null || fields.isEmpty()) {
      return List.of();
    }
    Map<String, FieldMapInput> byKey = new LinkedHashMap<>();
    boolean phoneOrEmail = false;
    for (FieldMapInput field : fields) {
      if (field == null || field.externalKey() == null || field.externalKey().isBlank()) {
        continue;
      }
      String target = field.target() == null ? "" : field.target().trim().toUpperCase(Locale.ROOT);
      if (!TARGETS.contains(target)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown field target " + target);
      }
      if ("CONTACT_PHONE".equals(target) || "CONTACT_EMAIL".equals(target)) {
        phoneOrEmail = true;
      }
      byKey.put(field.externalKey().trim().toLowerCase(Locale.ROOT), field);
    }
    if (!byKey.isEmpty() && !phoneOrEmail) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Map a mobile or an email");
    }
    return new ArrayList<>(byKey.values());
  }

  private WebFormResponse toResponse(CrmWebFormEntity form) {
    List<FieldMapResponse> fields =
        fieldMapRepository.findByFormIdOrderByIdAsc(form.getId()).stream()
            .map(row -> new FieldMapResponse(row.getId(), row.getExternalKey(), row.getTarget(), row.isRequired()))
            .toList();
    return new WebFormResponse(
        form.getId(),
        form.getName(),
        form.getPublicKey(),
        form.getStatus(),
        form.getBranchId(),
        form.getCampaignId(),
        form.getDefaultCourse(),
        form.getHoneypotField(),
        "/api/v1/crm/public/capture/" + form.getPublicKey(),
        fields,
        form.getCreatedAt());
  }

  private static String honeypot(String value) {
    if (value == null || value.isBlank()) {
      return "company_website";
    }
    String text = value.trim();
    return text.length() <= 64 ? text : text.substring(0, 64);
  }

  private static String blank(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
