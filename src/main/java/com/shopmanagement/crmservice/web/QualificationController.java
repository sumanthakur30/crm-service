package com.shopmanagement.crmservice.web;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.shopmanagement.crmservice.api.CrmLeadApi.LeadResponse;
import com.shopmanagement.crmservice.entitlement.CrmEntitlementGuard;
import com.shopmanagement.crmservice.service.QualificationSchemaService;

@RestController
@RequestMapping("/api/v1/crm/qualification")
public class QualificationController {

  private final QualificationSchemaService qualificationSchemaService;
  private final CrmEntitlementGuard entitlementGuard;

  public QualificationController(
      QualificationSchemaService qualificationSchemaService, CrmEntitlementGuard entitlementGuard) {
    this.qualificationSchemaService = qualificationSchemaService;
    this.entitlementGuard = entitlementGuard;
  }

  @GetMapping("/schemas")
  public List<Map<String, Object>> schemas() {
    entitlementGuard.requireCrmAccess();
    return qualificationSchemaService.listOrEnsure();
  }

  @PostMapping("/schemas")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> upsertSchema(@RequestBody Map<String, Object> body) {
    entitlementGuard.requireCrmAccess();
    return qualificationSchemaService.upsert(body);
  }

  @PostMapping("/leads/{leadId}")
  public LeadResponse saveLeadAnswers(
      @PathVariable Long leadId, @RequestBody Map<String, Object> body) {
    entitlementGuard.requireCrmAccess();
    return qualificationSchemaService.saveLeadAnswers(leadId, body);
  }
}
