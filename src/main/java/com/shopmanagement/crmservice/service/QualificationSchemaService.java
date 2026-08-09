package com.shopmanagement.crmservice.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.shopmanagement.crmservice.api.CrmLeadApi.LeadResponse;
import com.shopmanagement.crmservice.persistence.entity.CrmLeadEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmQualificationSchemaEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmLeadRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmQualificationSchemaRepository;
import com.shopmanagement.crmservice.support.TenantIds;

@Service
public class QualificationSchemaService {

  private final CrmQualificationSchemaRepository schemaRepository;
  private final CrmLeadRepository leadRepository;
  private final CrmLeadService leadService;
  private final TimelineService timelineService;

  public QualificationSchemaService(
      CrmQualificationSchemaRepository schemaRepository,
      CrmLeadRepository leadRepository,
      CrmLeadService leadService,
      TimelineService timelineService) {
    this.schemaRepository = schemaRepository;
    this.leadRepository = leadRepository;
    this.leadService = leadService;
    this.timelineService = timelineService;
  }

  @Transactional
  public List<Map<String, Object>> listOrEnsure() {
    String tenantId = TenantIds.require();
    ensureDefaults(tenantId);
    return schemaRepository.findByTenantIdAndDeletedAtIsNullOrderByCodeAsc(tenantId).stream()
        .map(QualificationSchemaService::toMap)
        .toList();
  }

  @Transactional
  public Map<String, Object> upsert(Map<String, Object> body) {
    String tenantId = TenantIds.require();
    Long id = body.get("id") == null ? null : Long.valueOf(String.valueOf(body.get("id")));
    CrmQualificationSchemaEntity schema;
    if (id != null) {
      schema =
          schemaRepository
              .findByTenantIdAndIdAndDeletedAtIsNull(tenantId, id)
              .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Schema not found"));
    } else {
      String code = String.valueOf(body.getOrDefault("code", "")).trim().toUpperCase(Locale.ROOT);
      if (code.isBlank()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "code is required");
      }
      schema =
          schemaRepository
              .findByTenantIdAndCodeAndDeletedAtIsNull(tenantId, code)
              .orElseGet(
                  () -> {
                    CrmQualificationSchemaEntity created = new CrmQualificationSchemaEntity();
                    created.setTenantId(tenantId);
                    created.setCode(code);
                    return created;
                  });
    }
    if (body.get("name") != null) {
      String name = String.valueOf(body.get("name")).trim();
      if (!name.isBlank()) {
        schema.setName(name);
      }
    }
    if (schema.getName() == null || schema.getName().isBlank()) {
      schema.setName(schema.getCode());
    }
    if (body.get("active") != null) {
      schema.setActive(Boolean.parseBoolean(String.valueOf(body.get("active"))));
    }
    if (body.get("fields") instanceof List<?> list) {
      schema.setFieldsJson(copyFields(list));
    } else if (body.get("fieldsJson") instanceof List<?> list) {
      schema.setFieldsJson(copyFields(list));
    }
    if (schema.getFieldsJson() == null) {
      schema.setFieldsJson(new ArrayList<>());
    }
    schema.touch();
    return toMap(schemaRepository.save(schema));
  }

  @Transactional
  public LeadResponse saveLeadAnswers(Long leadId, Map<String, Object> body) {
    String tenantId = TenantIds.require();
    CrmLeadEntity lead =
        leadRepository
            .findByTenantIdAndIdAndDeletedAtIsNull(tenantId, leadId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead not found"));
    String schemaCode =
        String.valueOf(body.getOrDefault("schemaCode", "BANT")).trim().toUpperCase(Locale.ROOT);
    ensureDefaults(tenantId);
    CrmQualificationSchemaEntity schema =
        schemaRepository
            .findByTenantIdAndCodeAndDeletedAtIsNull(tenantId, schemaCode)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Qualification schema not found"));
    if (!schema.isActive()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Schema is inactive");
    }
    @SuppressWarnings("unchecked")
    Map<String, Object> answers =
        body.get("answers") instanceof Map<?, ?> m
            ? new LinkedHashMap<>((Map<String, Object>) m)
            : new LinkedHashMap<>();
    Map<String, Object> attrs =
        lead.getAttributes() == null
            ? new LinkedHashMap<>()
            : new LinkedHashMap<>(lead.getAttributes());
    Map<String, Object> qualification = new LinkedHashMap<>();
    qualification.put("schemaCode", schema.getCode());
    qualification.put("schemaName", schema.getName());
    qualification.put("answers", answers);
    qualification.put("updatedAt", Instant.now().toString());
    attrs.put("qualification", qualification);
    lead.setAttributes(attrs);
    lead.touch();
    leadRepository.save(lead);
    timelineService.recordEvent(
        "LEAD",
        leadId,
        "QUALIFICATION_UPDATED",
        "Qualification " + schema.getCode() + " updated",
        Map.of("schemaCode", schema.getCode(), "answerKeys", answers.keySet()));
    return leadService.get(leadId);
  }

  private void ensureDefaults(String tenantId) {
    if (schemaRepository.findByTenantIdAndCodeAndDeletedAtIsNull(tenantId, "BANT").isPresent()) {
      return;
    }
    CrmQualificationSchemaEntity bant = new CrmQualificationSchemaEntity();
    bant.setTenantId(tenantId);
    bant.setCode("BANT");
    bant.setName("BANT qualification");
    bant.setActive(true);
    List<Map<String, Object>> fields = new ArrayList<>();
    fields.add(field("budget", "Budget", "text", false));
    fields.add(field("authority", "Authority", "text", false));
    fields.add(field("need", "Need", "textarea", false));
    fields.add(field("timeline", "Timeline", "text", false));
    bant.setFieldsJson(fields);
    schemaRepository.save(bant);
  }

  private static Map<String, Object> field(String key, String label, String type, boolean required) {
    Map<String, Object> f = new LinkedHashMap<>();
    f.put("key", key);
    f.put("label", label);
    f.put("type", type);
    f.put("required", required);
    return f;
  }

  private static List<Map<String, Object>> copyFields(List<?> list) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (Object item : list) {
      if (item instanceof Map<?, ?> m) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : m.entrySet()) {
          copy.put(String.valueOf(e.getKey()), e.getValue());
        }
        out.add(copy);
      }
    }
    return out;
  }

  private static Map<String, Object> toMap(CrmQualificationSchemaEntity s) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", s.getId());
    m.put("code", s.getCode());
    m.put("name", s.getName());
    m.put("active", s.isActive());
    m.put("fields", s.getFieldsJson() == null ? List.of() : s.getFieldsJson());
    return m;
  }
}
