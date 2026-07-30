package com.shopmanagement.crmservice.web;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shopmanagement.crmservice.entitlement.CrmEntitlementGuard;
import com.shopmanagement.crmservice.service.AiAssistService;

@RestController
@RequestMapping("/api/v1/crm/ai")
public class AiAssistController {

  private final AiAssistService aiAssistService;
  private final CrmEntitlementGuard entitlementGuard;

  public AiAssistController(AiAssistService aiAssistService, CrmEntitlementGuard entitlementGuard) {
    this.aiAssistService = aiAssistService;
    this.entitlementGuard = entitlementGuard;
  }

  @PostMapping("/leads/{leadId}/summarize")
  public Map<String, Object> summarizeLead(
      @PathVariable Long leadId, @RequestParam(required = false) String language) {
    entitlementGuard.requireCrmAccess();
    return aiAssistService.summarizeLead(leadId, language);
  }

  @PostMapping("/leads/{leadId}/nba")
  public Map<String, Object> nba(
      @PathVariable Long leadId, @RequestParam(required = false) String language) {
    entitlementGuard.requireCrmAccess();
    return aiAssistService.nextBestAction(leadId, language);
  }

  @PostMapping("/leads/{leadId}/score-explain")
  public Map<String, Object> scoreExplain(
      @PathVariable Long leadId, @RequestParam(required = false) String language) {
    entitlementGuard.requireCrmAccess();
    return aiAssistService.explainScore(leadId, language);
  }

  @PostMapping("/leads/{leadId}/churn-upsell")
  public Map<String, Object> churnUpsell(
      @PathVariable Long leadId, @RequestParam(required = false) String language) {
    entitlementGuard.requireCrmAccess();
    return aiAssistService.churnUpsell(leadId, language);
  }

  @PostMapping("/leads/{leadId}/draft")
  public Map<String, Object> draft(
      @PathVariable Long leadId,
      @RequestParam(required = false, defaultValue = "WHATSAPP") String channel,
      @RequestParam(required = false) String language) {
    entitlementGuard.requireCrmAccess();
    return aiAssistService.draftMessage(leadId, channel, language);
  }

  @PostMapping("/opportunities/{opportunityId}/win-predict")
  public Map<String, Object> winPredict(
      @PathVariable Long opportunityId, @RequestParam(required = false) String language) {
    entitlementGuard.requireCrmAccess();
    return aiAssistService.winPredict(opportunityId, language);
  }

  @PostMapping("/ocr/card")
  public Map<String, Object> ocrCard(
      @RequestBody Map<String, Object> body, @RequestParam(required = false) String language) {
    entitlementGuard.requireCrmAccess();
    return aiAssistService.ocrCard(body, language);
  }

  @PostMapping("/copilot")
  public Map<String, Object> copilot(@RequestBody Map<String, Object> body) {
    entitlementGuard.requireCrmAccess();
    String question = body.get("question") == null ? "" : String.valueOf(body.get("question"));
    Long leadId = body.get("leadId") == null ? null : ((Number) body.get("leadId")).longValue();
    Long opportunityId =
        body.get("opportunityId") == null ? null : ((Number) body.get("opportunityId")).longValue();
    String language = body.get("language") == null ? null : String.valueOf(body.get("language"));
    return aiAssistService.copilot(question, leadId, opportunityId, language);
  }

  @GetMapping("/insights")
  public List<Map<String, Object>> insights(
      @RequestParam String relatedType, @RequestParam Long relatedId) {
    entitlementGuard.requireCrmAccess();
    return aiAssistService.listInsights(relatedType, relatedId);
  }
}
