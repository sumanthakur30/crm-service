package com.shopmanagement.crmservice.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.shopmanagement.crmservice.api.CrmCampaignApi.CampaignResponse;
import com.shopmanagement.crmservice.api.CrmCampaignApi.CampaignUpsert;
import com.shopmanagement.crmservice.entitlement.CrmEntitlementGuard;
import com.shopmanagement.crmservice.service.CampaignService;
import com.shopmanagement.crmservice.service.PublicCaptureService;
import com.shopmanagement.crmservice.service.WebFormCaptureService.Result;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/crm")
public class CampaignController {

  private final CampaignService campaignService;
  private final PublicCaptureService publicCaptureService;
  private final CrmEntitlementGuard entitlementGuard;

  public CampaignController(
      CampaignService campaignService,
      PublicCaptureService publicCaptureService,
      CrmEntitlementGuard entitlementGuard) {
    this.campaignService = campaignService;
    this.publicCaptureService = publicCaptureService;
    this.entitlementGuard = entitlementGuard;
  }

  @GetMapping("/campaigns")
  public List<CampaignResponse> list() {
    entitlementGuard.requireCampaignAccess();
    return campaignService.list();
  }

  @GetMapping("/campaigns/{id}")
  public CampaignResponse get(@PathVariable Long id) {
    entitlementGuard.requireCampaignAccess();
    return campaignService.get(id);
  }

  @PostMapping("/campaigns")
  @ResponseStatus(HttpStatus.CREATED)
  public CampaignResponse upsert(@Valid @RequestBody CampaignUpsert body) {
    entitlementGuard.requireCampaignAccess();
    return campaignService.upsert(body);
  }

  /** Public capture. A form key uses the field map. Any other key stays a campaign capture. */
  @PostMapping(value = "/public/capture/{publicKey}", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> captureJson(
      @PathVariable String publicKey,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody Map<String, Object> body,
      HttpServletRequest request) {
    Map<String, Object> fields = new LinkedHashMap<>(body == null ? Map.of() : body);
    copyQueryUtms(request, fields);
    return respond(publicCaptureService.capture(publicKey, fields, idempotencyKey));
  }

  @PostMapping(value = "/public/capture/{publicKey}", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  public ResponseEntity<?> captureForm(
      @PathVariable String publicKey,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      HttpServletRequest request) {
    Map<String, Object> fields = new LinkedHashMap<>();
    request
        .getParameterMap()
        .forEach(
            (name, values) -> {
              if (values != null && values.length > 0 && values[0] != null && !values[0].isBlank()) {
                fields.put(name, values[0].trim());
              }
            });
    return respond(publicCaptureService.capture(publicKey, fields, idempotencyKey));
  }

  private static ResponseEntity<?> respond(Result result) {
    if (result.dropped()) {
      return ResponseEntity.ok(Map.of("accepted", true));
    }
    if (result.duplicate()) {
      return ResponseEntity.ok(result.lead());
    }
    return ResponseEntity.status(HttpStatus.CREATED).body(result.lead());
  }

  private static void copyQueryUtms(HttpServletRequest request, Map<String, Object> fields) {
    if (request == null) {
      return;
    }
    for (String name : List.of("utm_source", "utm_medium", "utm_campaign", "utm_content", "utm_term")) {
      if (fields.containsKey(name)) {
        continue;
      }
      String value = request.getParameter(name);
      if (value != null && !value.isBlank()) {
        fields.put(name, value.trim());
      }
    }
  }
}
