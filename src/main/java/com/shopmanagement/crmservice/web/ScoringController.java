package com.shopmanagement.crmservice.web;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shopmanagement.crmservice.api.CrmLeadApi.LeadResponse;
import com.shopmanagement.crmservice.entitlement.CrmEntitlementGuard;
import com.shopmanagement.crmservice.service.BehaviorScoringService;

@RestController
@RequestMapping("/api/v1/crm/scoring")
public class ScoringController {

  private final BehaviorScoringService scoringService;
  private final CrmEntitlementGuard entitlementGuard;

  public ScoringController(BehaviorScoringService scoringService, CrmEntitlementGuard entitlementGuard) {
    this.scoringService = scoringService;
    this.entitlementGuard = entitlementGuard;
  }

  @PostMapping("/rules/ensure-defaults")
  public List<Map<String, Object>> ensureDefaults() {
    entitlementGuard.requireCrmAccess();
    return scoringService.ensureDefaultRules();
  }

  @GetMapping("/rules")
  public List<Map<String, Object>> rules() {
    entitlementGuard.requireCrmAccess();
    return scoringService.listRules();
  }

  @PostMapping("/leads/{leadId}/events")
  public LeadResponse applyEvent(@PathVariable Long leadId, @RequestBody Map<String, Object> body) {
    entitlementGuard.requireCrmAccess();
    String eventType = String.valueOf(body.getOrDefault("eventType", ""));
    String summary = body.get("summary") == null ? null : String.valueOf(body.get("summary"));
    @SuppressWarnings("unchecked")
    Map<String, Object> payload =
        body.get("payload") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    return scoringService.applyEvent(leadId, eventType, summary, payload);
  }

  @PostMapping("/leads/{leadId}/rescore")
  public LeadResponse rescore(@PathVariable Long leadId) {
    entitlementGuard.requireCrmAccess();
    return scoringService.rescore(leadId);
  }

  @GetMapping("/leads/{leadId}/events")
  public List<Map<String, Object>> events(@PathVariable Long leadId) {
    entitlementGuard.requireCrmAccess();
    return scoringService.eventsForLead(leadId);
  }
}
