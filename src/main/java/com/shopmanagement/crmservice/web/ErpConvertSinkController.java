package com.shopmanagement.crmservice.web;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Local ERP convert sinks for Retail pilot when shop/school/FF receivers are offline.
 * Point {@code crm.convert.*-url} here (see application-pilot-retail.properties).
 */
@RestController
@RequestMapping("/api/v1/crm/adapters/erp")
public class ErpConvertSinkController {

  @PostMapping("/{target}")
  public Map<String, Object> accept(
      @PathVariable String target, @RequestBody(required = false) Map<String, Object> body) {
    String t = target == null ? "UNKNOWN" : target.trim().toUpperCase(Locale.ROOT);
    Map<String, Object> response = new LinkedHashMap<>();
    String id = "CRM-SINK-" + t + "-" + UUID.randomUUID().toString().substring(0, 8);
    response.put("id", id);
    response.put("externalId", id);
    response.put("targetSystem", t);
    response.put("status", "ACCEPTED");
    response.put("sink", true);
    response.put("note", "Local CRM ERP sink — swap URL to live shop/school/FF from-crm when ready");
    if (body != null) {
      response.put("crmLeadId", body.get("crmLeadId"));
      response.put("correlationId", body.get("correlationId"));
    }
    return response;
  }
}
