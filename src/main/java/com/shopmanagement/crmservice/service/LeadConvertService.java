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
import com.shopmanagement.crmservice.filter.TenantContextFilter;
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

    Map<String, Object> prior = priorSuccessfulConvert(lead, target);
    if (prior != null) {
      Map<String, Object> ack = new LinkedHashMap<>(prior);
      ack.put("alreadyConverted", true);
      ack.put("convertEnabled", properties.isEnabled());
      ack.put("mode", priorMode(prior));
      return ack;
    }

    String correlationId = UUID.randomUUID().toString();
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("crmLeadId", lead.getId());
    request.put("tenantId", tenantId);
    request.put("title", lead.getTitle());
    request.put("displayName", lead.getDisplayName());
    request.put("companyName", lead.getCompanyName());
    request.put("email", lead.getEmail());
    request.put("phone", lead.getPhone());
    request.put("sourceCode", lead.getSourceCode());
    request.put("correlationId", correlationId);

    CrmConvertEventEntity event = new CrmConvertEventEntity();
    event.setTenantId(tenantId);
    event.setLeadId(lead.getId());
    event.setTargetSystem(target);
    event.setRequestJson(request);

    if (!properties.isEnabled()) {
      event.setStatus("SKIPPED");
      event.setResponseJson(Map.of("note", "crm.convert.enabled=false — payload stored only"));
      event = convertEventRepository.save(event);
      stashRef(lead, target, event, false);
      return toResponse(event, false, "DISABLED");
    }

    String url = resolveUrl(target);
    String mode = isSinkUrl(url) ? "SINK" : "LIVE";
    String shopId = resolveShopId(tenantId);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("X-Tenant-Id", tenantId);
    headers.set("X-Shop-Id", shopId);
    if (properties.getInternalApiKey() != null && !properties.getInternalApiKey().isBlank()) {
      headers.set("X-Internal-Api-Key", properties.getInternalApiKey().trim());
    }

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
        throw new ResponseStatusException(
            HttpStatus.BAD_GATEWAY, "ERP convert failed: " + event.getErrorMessage());
      }
    }

    event = convertEventRepository.save(event);
    boolean markConverted = "SENT".equals(event.getStatus());
    stashRef(lead, target, event, markConverted);
    timelineService.recordEvent(
        "LEAD",
        lead.getId(),
        "LEAD_CONVERTED",
        "Convert to " + target + " · " + event.getStatus() + " · " + mode,
        Map.of(
            "eventId",
            event.getId(),
            "targetSystem",
            target,
            "status",
            event.getStatus(),
            "mode",
            mode,
            "externalId",
            event.getExternalId() == null ? "" : event.getExternalId()));
    return toResponse(event, false, mode);
  }

  private Map<String, Object> priorSuccessfulConvert(CrmLeadEntity lead, String target) {
    Map<String, Object> refs =
        lead.getExternalRefs() == null ? Map.of() : lead.getExternalRefs();
    Object entry = refs.get(target);
    if (!(entry instanceof Map<?, ?> map)) {
      return null;
    }
    Object status = map.get("status");
    Object externalId = map.get("externalId");
    if (!"SENT".equals(String.valueOf(status)) || externalId == null || String.valueOf(externalId).isBlank()) {
      return null;
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("id", map.get("eventId"));
    out.put("leadId", lead.getId());
    out.put("targetSystem", target);
    out.put("status", "ACKED");
    out.put("externalId", String.valueOf(externalId));
    out.put("errorMessage", null);
    out.put("request", Map.of());
    out.put("response", Map.of("note", "Lead already converted to " + target + " — skipped duplicate"));
    return out;
  }

  private static String priorMode(Map<String, Object> prior) {
    Object response = prior.get("response");
    if (response instanceof Map<?, ?> m && Boolean.TRUE.equals(m.get("sink"))) {
      return "SINK";
    }
    return "LIVE";
  }

  private String resolveUrl(String target) {
    return switch (target) {
      case "SHOP_CUSTOMER" -> properties.getShopCustomerUrl();
      case "SCHOOL_INQUIRY" -> properties.getSchoolInquiryUrl();
      default -> properties.getFieldForceUrl();
    };
  }

  private static boolean isSinkUrl(String url) {
    return url != null && url.contains("/adapters/erp/");
  }

  private String resolveShopId(String tenantId) {
    if (properties.getShopId() != null && !properties.getShopId().isBlank()) {
      return properties.getShopId().trim();
    }
    String fromRequest = TenantContextFilter.getCurrentShopId();
    if (fromRequest != null && !fromRequest.isBlank()) {
      return fromRequest.trim();
    }
    return tenantId;
  }

  private void stashRef(
      CrmLeadEntity lead, String target, CrmConvertEventEntity event, boolean markConverted) {
    Map<String, Object> refs =
        new LinkedHashMap<>(lead.getExternalRefs() == null ? Map.of() : lead.getExternalRefs());
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("eventId", event.getId());
    entry.put("status", event.getStatus());
    entry.put("externalId", event.getExternalId());
    refs.put(target, entry);
    lead.setExternalRefs(refs);
    if (markConverted) {
      lead.setStatus("CONVERTED");
    }
    lead.touch();
    leadRepository.save(lead);
  }

  private Map<String, Object> toResponse(
      CrmConvertEventEntity e, boolean alreadyConverted, String mode) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", e.getId());
    m.put("leadId", e.getLeadId());
    m.put("targetSystem", e.getTargetSystem());
    m.put("status", e.getStatus());
    m.put("externalId", e.getExternalId());
    m.put("errorMessage", e.getErrorMessage());
    m.put("request", e.getRequestJson());
    m.put("response", e.getResponseJson());
    m.put("alreadyConverted", alreadyConverted);
    m.put("convertEnabled", properties.isEnabled());
    m.put("mode", mode);
    return m;
  }
}
