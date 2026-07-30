package com.shopmanagement.crmservice.service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.shopmanagement.crmservice.config.CrmConvertProperties;
import com.shopmanagement.crmservice.persistence.entity.CrmConvertEventEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmLeadEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmConvertEventRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmLeadRepository;
import com.shopmanagement.crmservice.support.TenantIds;

@Service
public class LeadConvertService {

  private static final Set<String> TARGETS = Set.of("SHOP_CUSTOMER", "SCHOOL_INQUIRY", "FIELD_FORCE");

  private final CrmLeadRepository leadRepository;
  private final CrmConvertEventRepository convertEventRepository;
  private final CrmConvertProperties properties;
  private final RestTemplate restTemplate;
  private final TimelineService timelineService;

  public LeadConvertService(
      CrmLeadRepository leadRepository,
      CrmConvertEventRepository convertEventRepository,
      CrmConvertProperties properties,
      RestTemplate crmRestTemplate,
      TimelineService timelineService) {
    this.leadRepository = leadRepository;
    this.convertEventRepository = convertEventRepository;
    this.properties = properties;
    this.restTemplate = crmRestTemplate;
    this.timelineService = timelineService;
  }

  @Transactional
  public Map<String, Object> convert(Long leadId, String targetSystem) {
    String tenantId = TenantIds.require();
    String target = targetSystem == null ? "" : targetSystem.trim().toUpperCase(Locale.ROOT);
    if (!TARGETS.contains(target)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "targetSystem must be SHOP_CUSTOMER, SCHOOL_INQUIRY, or FIELD_FORCE");
    }
    CrmLeadEntity lead =
        leadRepository
            .findByTenantIdAndIdAndDeletedAtIsNull(tenantId, leadId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead not found"));

    Map<String, Object> request = new LinkedHashMap<>();
    request.put("crmLeadId", lead.getId());
    request.put("tenantId", tenantId);
    request.put("title", lead.getTitle());
    request.put("displayName", lead.getDisplayName());
    request.put("companyName", lead.getCompanyName());
    request.put("email", lead.getEmail());
    request.put("phone", lead.getPhone());
    request.put("sourceCode", lead.getSourceCode());
    request.put("correlationId", UUID.randomUUID().toString());

    CrmConvertEventEntity event = new CrmConvertEventEntity();
    event.setTenantId(tenantId);
    event.setLeadId(lead.getId());
    event.setTargetSystem(target);
    event.setRequestJson(request);

    if (!properties.isEnabled()) {
      event.setStatus("SKIPPED");
      event.setResponseJson(Map.of("note", "crm.convert.enabled=false — payload stored only"));
      event = convertEventRepository.save(event);
      stashRef(lead, target, event);
      return toResponse(event);
    }

    String url =
        switch (target) {
          case "SHOP_CUSTOMER" -> properties.getShopCustomerUrl();
          case "SCHOOL_INQUIRY" -> properties.getSchoolInquiryUrl();
          default -> properties.getFieldForceUrl();
        };

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("X-Tenant-Id", tenantId);
    headers.set("X-Shop-Id", tenantId);

    try {
      ResponseEntity<Map<String, Object>> response =
          restTemplate.exchange(
              url,
              HttpMethod.POST,
              new HttpEntity<>(request, headers),
              new ParameterizedTypeReference<Map<String, Object>>() {});
      Map<String, Object> body = response.getBody() == null ? Map.of() : response.getBody();
      event.setStatus("SENT");
      event.setResponseJson(new LinkedHashMap<>(body));
      Object externalId = body.get("id");
      if (externalId == null) {
        externalId = body.get("externalId");
      }
      if (externalId != null) {
        event.setExternalId(String.valueOf(externalId));
      }
    } catch (RestClientException ex) {
      event.setStatus("FAILED");
      event.setErrorMessage(
          ex.getMessage() == null
              ? "convert failed"
              : ex.getMessage().substring(0, Math.min(500, ex.getMessage().length())));
      event.setResponseJson(Map.of("error", event.getErrorMessage()));
      if (!properties.isFailOpen()) {
        convertEventRepository.save(event);
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "ERP convert failed: " + event.getErrorMessage());
      }
    }

    event = convertEventRepository.save(event);
    stashRef(lead, target, event);
    timelineService.recordEvent(
        "LEAD",
        lead.getId(),
        "LEAD_CONVERTED",
        "Convert to " + target + " · " + event.getStatus(),
        Map.of("eventId", event.getId(), "targetSystem", target, "status", event.getStatus()));
    return toResponse(event);
  }

  private void stashRef(CrmLeadEntity lead, String target, CrmConvertEventEntity event) {
    Map<String, Object> refs =
        new LinkedHashMap<>(lead.getExternalRefs() == null ? Map.of() : lead.getExternalRefs());
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("eventId", event.getId());
    entry.put("status", event.getStatus());
    entry.put("externalId", event.getExternalId());
    refs.put(target, entry);
    lead.setExternalRefs(refs);
    lead.touch();
    leadRepository.save(lead);
  }

  private static Map<String, Object> toResponse(CrmConvertEventEntity e) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", e.getId());
    m.put("leadId", e.getLeadId());
    m.put("targetSystem", e.getTargetSystem());
    m.put("status", e.getStatus());
    m.put("externalId", e.getExternalId());
    m.put("errorMessage", e.getErrorMessage());
    m.put("request", e.getRequestJson());
    m.put("response", e.getResponseJson());
    return m;
  }
}
