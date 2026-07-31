package com.shopmanagement.crmservice.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.shopmanagement.crmservice.config.CrmCtiProperties;
import com.shopmanagement.crmservice.cti.CtiClient;
import com.shopmanagement.crmservice.support.TenantIds;

@Service
public class CtiService {

  private final CrmCtiProperties properties;
  private final CtiClient ctiClient;
  private final TimelineService timelineService;

  public CtiService(CrmCtiProperties properties, CtiClient ctiClient, TimelineService timelineService) {
    this.properties = properties;
    this.ctiClient = ctiClient;
    this.timelineService = timelineService;
  }

  public boolean isEnabled() {
    return properties.isEnabled();
  }

  public String provider() {
    String p = properties.getProvider();
    return p == null || p.isBlank() ? "STUB" : p.trim();
  }

  @Transactional
  public Map<String, Object> clickToDial(Map<String, Object> body) {
    if (!properties.isEnabled()) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "CTI is disabled — set CRM_CTI_ENABLED=true");
    }
    String phone = str(body.get("phone"));
    if (phone == null || phone.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "phone is required");
    }
    phone = phone.trim();
    String tenantId = TenantIds.require();
    Long leadId = longOrNull(body.get("leadId"));
    Long opportunityId = longOrNull(body.get("opportunityId"));
    Long caseId = longOrNull(body.get("caseId"));

    Map<String, Object> context = new LinkedHashMap<>();
    if (leadId != null) {
      context.put("leadId", leadId);
    }
    if (opportunityId != null) {
      context.put("opportunityId", opportunityId);
    }
    if (caseId != null) {
      context.put("caseId", caseId);
    }

    Map<String, Object> dial = ctiClient.clickToDial(tenantId, phone, context);
    if (leadId != null) {
      timelineService.recordEvent(
          "LEAD",
          leadId,
          "CTI_CLICK_TO_DIAL",
          "CTI click-to-dial " + phone,
          Map.of(
              "phone",
              phone,
              "callId",
              dial.get("callId") == null ? "" : String.valueOf(dial.get("callId")),
              "provider",
              provider()));
    }

    Map<String, Object> out = new LinkedHashMap<>(dial);
    out.put("ctiEnabled", true);
    out.put("provider", provider());
    return out;
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
}
