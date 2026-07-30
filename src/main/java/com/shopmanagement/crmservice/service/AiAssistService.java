package com.shopmanagement.crmservice.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.shopmanagement.crmservice.config.CrmAiProperties;
import com.shopmanagement.crmservice.integration.AiHttpClient;
import com.shopmanagement.crmservice.persistence.entity.CrmAiInsightEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmLeadEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmOpportunityEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmTenantEnterpriseEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmAiInsightRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmLeadRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmOpportunityRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmScoreEventRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmTenantEnterpriseRepository;
import com.shopmanagement.crmservice.support.TenantIds;

@Service
public class AiAssistService {

  private final CrmAiProperties aiProperties;
  private final CrmAiInsightRepository insightRepository;
  private final CrmLeadRepository leadRepository;
  private final CrmOpportunityRepository opportunityRepository;
  private final CrmScoreEventRepository scoreEventRepository;
  private final CrmTenantEnterpriseRepository enterpriseRepository;
  private final TimelineService timelineService;
  private final AiHttpClient aiHttpClient;

  public AiAssistService(
      CrmAiProperties aiProperties,
      CrmAiInsightRepository insightRepository,
      CrmLeadRepository leadRepository,
      CrmOpportunityRepository opportunityRepository,
      CrmScoreEventRepository scoreEventRepository,
      CrmTenantEnterpriseRepository enterpriseRepository,
      TimelineService timelineService,
      AiHttpClient aiHttpClient) {
    this.aiProperties = aiProperties;
    this.insightRepository = insightRepository;
    this.leadRepository = leadRepository;
    this.opportunityRepository = opportunityRepository;
    this.scoreEventRepository = scoreEventRepository;
    this.enterpriseRepository = enterpriseRepository;
    this.timelineService = timelineService;
    this.aiHttpClient = aiHttpClient;
  }

  @Transactional
  public Map<String, Object> summarizeLead(Long leadId, String language) {
    requireAi();
    String tenantId = TenantIds.require();
    CrmLeadEntity lead = requireLead(tenantId, leadId);
    String lang = normalizeLang(language, tenantId);
    int score = lead.getScore();
    String body =
        "Lead #"
            + lead.getId()
            + " \""
            + lead.getTitle()
            + "\" is "
            + lead.getStatus()
            + " (score "
            + score
            + "). Contact: "
            + nvl(lead.getDisplayName(), "-")
            + " / "
            + nvl(lead.getPhone(), "-")
            + " / "
            + nvl(lead.getEmail(), "-")
            + ". Source "
            + nvl(lead.getSourceCode(), "UNKNOWN")
            + (lead.getUtmSource() != null ? " · UTM " + lead.getUtmSource() + "/" + nvl(lead.getUtmMedium(), "-") : "")
            + ".";
    body = localize(body, lang);
    Map<String, Object> http =
        aiHttpClient.complete(
            "SUMMARY",
            lang,
            Map.of("leadId", leadId, "title", lead.getTitle(), "score", score, "status", lead.getStatus()));
    if (http.get("body") != null) {
      body = String.valueOf(http.get("body"));
    }
    String title = http.get("title") != null ? String.valueOf(http.get("title")) : "Lead summary";
    return persist(
        "LEAD",
        leadId,
        "SUMMARY",
        title,
        body,
        confidence(0.72 + Math.min(0.2, score / 500.0)),
        lang,
        Map.of("score", score, "status", lead.getStatus(), "provider", aiProperties.getProvider()));
  }

  @Transactional
  public Map<String, Object> nextBestAction(Long leadId, String language) {
    requireAi();
    String tenantId = TenantIds.require();
    CrmLeadEntity lead = requireLead(tenantId, leadId);
    String lang = normalizeLang(language, tenantId);
    String action;
    String reason;
    if (lead.getPhone() == null || lead.getPhone().isBlank()) {
      action = "Capture phone number";
      reason = "No phone on file — blocks WhatsApp sequences and call logging.";
    } else if (lead.getScore() < 30) {
      action = "Send welcome sequence + book discovery call";
      reason = "Low engagement score (" + lead.getScore() + ").";
    } else if (lead.getScore() < 60) {
      action = "Share GST quotation draft";
      reason = "Mid score — convert interest into a priced proposal.";
    } else {
      action = "Request manager approval and close";
      reason = "High score (" + lead.getScore() + ") — push to opportunity won.";
    }
    String body = "NBA: " + action + ". Why: " + reason;
    body = localize(body, lang);
    return persist(
        "LEAD",
        leadId,
        "NBA",
        action,
        body,
        confidence(0.68),
        lang,
        Map.of("action", action, "reason", reason));
  }

  @Transactional
  public Map<String, Object> explainScore(Long leadId, String language) {
    requireAi();
    String tenantId = TenantIds.require();
    requireLead(tenantId, leadId);
    String lang = normalizeLang(language, tenantId);
    int sum = scoreEventRepository.sumPointsByTenantIdAndLeadId(tenantId, leadId);
    String body =
        "Behavior score total "
            + Math.max(0, Math.min(100, sum))
            + " from recorded score events. Recent events drive call/meeting/campaign bumps.";
    body = localize(body, lang);
    return persist(
        "LEAD",
        leadId,
        "SCORE_EXPLAIN",
        "Score explanation",
        body,
        confidence(0.8),
        lang,
        Map.of("pointsSum", sum));
  }

  @Transactional
  public Map<String, Object> winPredict(Long opportunityId, String language) {
    requireAi();
    String tenantId = TenantIds.require();
    CrmOpportunityEntity opp =
        opportunityRepository
            .findByTenantIdAndIdAndDeletedAtIsNull(tenantId, opportunityId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Opportunity not found"));
    String lang = normalizeLang(language, tenantId);
    double p = opp.getProbability() / 100.0;
    if (opp.getAmount() != null && opp.getAmount().compareTo(BigDecimal.valueOf(100000)) > 0) {
      p = Math.max(0.05, p - 0.05);
    }
    if ("OPEN".equalsIgnoreCase(opp.getStatus()) && opp.getExpectedCloseDate() != null) {
      p = Math.min(0.95, p + 0.05);
    }
    int pct = (int) Math.round(p * 100);
    String body =
        "Win probability ≈ "
            + pct
            + "% for \""
            + opp.getName()
            + "\" (stage probability "
            + opp.getProbability()
            + "%, amount "
            + nvl(String.valueOf(opp.getAmount()), "-")
            + ").";
    body = localize(body, lang);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("winProbability", pct);
    payload.put("stageProbability", opp.getProbability());
    return persist(
        "OPPORTUNITY",
        opportunityId,
        "WIN_PREDICTION",
        "Win prediction " + pct + "%",
        body,
        confidence(0.6 + p * 0.3),
        lang,
        payload);
  }

  @Transactional
  public Map<String, Object> churnUpsell(Long leadId, String language) {
    requireAi();
    String tenantId = TenantIds.require();
    CrmLeadEntity lead = requireLead(tenantId, leadId);
    String lang = normalizeLang(language, tenantId);
    boolean churn = lead.getScore() < 25 || "LOST".equalsIgnoreCase(lead.getStatus());
    String type = churn ? "CHURN_RISK" : "UPSELL";
    String title = churn ? "Churn / drop-off risk" : "Upsell opportunity";
    String body =
        churn
            ? "Lead looks cold (score "
                + lead.getScore()
                + ", status "
                + lead.getStatus()
                + "). Re-engage via WhatsApp sequence within 24h."
            : "Lead is warm (score "
                + lead.getScore()
                + "). Offer Professional plan add-on or multi-year quote.";
    body = localize(body, lang);
    return persist("LEAD", leadId, type, title, body, confidence(churn ? 0.7 : 0.65), lang, Map.of("churn", churn));
  }

  @Transactional
  public Map<String, Object> ocrCard(Map<String, Object> body, String language) {
    requireAi();
    String tenantId = TenantIds.require();
    String lang = normalizeLang(language, tenantId);
    // Stub OCR: accepts raw text / imageRef and extracts naive fields.
    String raw = body.get("text") == null ? "" : String.valueOf(body.get("text"));
    Map<String, Object> extracted = new LinkedHashMap<>();
    extracted.put("name", extractAfter(raw, "Name:"));
    extracted.put("phone", extractAfter(raw, "Phone:"));
    extracted.put("email", extractAfter(raw, "Email:"));
    extracted.put("company", extractAfter(raw, "Company:"));
    extracted.put("imageRef", body.get("imageRef"));
    String summary =
        "OCR card parsed "
            + extracted.entrySet().stream().filter(e -> e.getValue() != null && !String.valueOf(e.getValue()).isBlank()).count()
            + " fields (heuristic). Wire real OCR provider later.";
    summary = localize(summary, lang);
    Long relatedId = body.get("leadId") == null ? 0L : ((Number) body.get("leadId")).longValue();
    String relatedType = relatedId > 0 ? "LEAD" : "TENANT";
    return persist(
        relatedType,
        relatedId,
        "OCR_CARD",
        "Business card OCR",
        summary,
        confidence(0.55),
        lang,
        extracted);
  }

  @Transactional
  public Map<String, Object> draftMessage(Long leadId, String channel, String language) {
    requireAi();
    String tenantId = TenantIds.require();
    CrmLeadEntity lead = requireLead(tenantId, leadId);
    String lang = normalizeLang(language, tenantId);
    String ch = channel == null || channel.isBlank() ? "WHATSAPP" : channel.toUpperCase(Locale.ROOT);
    String name = nvl(lead.getDisplayName(), "there");
    String draft =
        switch (ch) {
          case "EMAIL" ->
              "Subject: Following up on "
                  + lead.getTitle()
                  + "\n\nHi "
                  + name
                  + ",\n\nThanks for your interest. I can share a GST quotation today — would a 15-min call work?\n\nRegards,\nSugamFlow CRM";
          case "SMS" -> "Hi " + name + ", thanks for enquiring about " + lead.getTitle() + ". Reply YES for a quote.";
          default ->
              "Hi "
                  + name
                  + " 👋 Thanks for your interest in "
                  + lead.getTitle()
                  + ". Shall I send a GST quotation on WhatsApp?";
        };
    draft = localize(draft, lang);
    Map<String, Object> http =
        aiHttpClient.complete(
            "DRAFT",
            lang,
            Map.of("leadId", leadId, "channel", ch, "name", name, "title", lead.getTitle()));
    if (http.get("body") != null) {
      draft = String.valueOf(http.get("body"));
    }
    return persist(
        "LEAD",
        leadId,
        "DRAFT",
        ch + " draft",
        draft,
        confidence(0.75),
        lang,
        Map.of("channel", ch, "provider", aiProperties.getProvider()));
  }

  @Transactional
  public Map<String, Object> copilot(String question, Long leadId, Long opportunityId, String language) {
    requireAi();
    String tenantId = TenantIds.require();
    String lang = normalizeLang(language, tenantId);
    String q = question == null ? "" : question.trim();
    String answer;
    if (leadId != null) {
      CrmLeadEntity lead = requireLead(tenantId, leadId);
      answer =
          "For lead \""
              + lead.getTitle()
              + "\" (score "
              + lead.getScore()
              + "): prioritize "
              + (lead.getScore() >= 60 ? "closing with a quote" : "a discovery call")
              + ". Q: "
              + q;
    } else if (opportunityId != null) {
      Map<String, Object> win = winPredict(opportunityId, lang);
      answer = "Deal guidance — " + win.get("body") + " Q: " + q;
    } else {
      answer =
          "Copilot tip: use Campaigns + UTM for source truth, Ops forecast for pipeline, and scoring events for NBA. Q: "
              + q;
    }
    answer = localize(answer, lang);
    long relatedId = leadId != null ? leadId : (opportunityId != null ? opportunityId : 0L);
    String relatedType = leadId != null ? "LEAD" : (opportunityId != null ? "OPPORTUNITY" : "TENANT");
    return persist(relatedType, relatedId, "COPILOT", "Copilot reply", answer, confidence(0.62), lang, Map.of("question", q));
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listInsights(String relatedType, Long relatedId) {
    requireAi();
    return insightRepository
        .findByTenantIdAndRelatedTypeAndRelatedIdOrderByCreatedAtDesc(
            TenantIds.require(), relatedType.toUpperCase(Locale.ROOT), relatedId)
        .stream()
        .map(AiAssistService::toInsight)
        .toList();
  }

  private Map<String, Object> persist(
      String relatedType,
      Long relatedId,
      String insightType,
      String title,
      String body,
      BigDecimal confidence,
      String lang,
      Map<String, Object> payload) {
    String tenantId = TenantIds.require();
    CrmAiInsightEntity e = new CrmAiInsightEntity();
    e.setTenantId(tenantId);
    e.setRelatedType(relatedType);
    e.setRelatedId(relatedId);
    e.setInsightType(insightType);
    e.setTitle(title);
    e.setBody(body);
    e.setConfidence(confidence);
    e.setModelCode(aiProperties.getModelCode());
    e.setLanguageCode(lang);
    e.setPayloadJson(new LinkedHashMap<>(payload));
    e = insightRepository.save(e);
    if ("LEAD".equals(relatedType) && relatedId != null && relatedId > 0) {
      timelineService.recordEvent(
          "LEAD", relatedId, "AI_" + insightType, title, Map.of("insightId", e.getId()));
    }
    return toInsight(e);
  }

  private void requireAi() {
    if (!aiProperties.isEnabled()) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "crm.ai.enabled=false");
    }
    CrmTenantEnterpriseEntity ent =
        enterpriseRepository.findById(TenantIds.require()).orElse(null);
    if (ent != null && !ent.isAiEnabled()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "AI disabled for tenant");
    }
  }

  private CrmLeadEntity requireLead(String tenantId, Long leadId) {
    return leadRepository
        .findByTenantIdAndIdAndDeletedAtIsNull(tenantId, leadId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead not found"));
  }

  private String normalizeLang(String language, String tenantId) {
    if (language != null && !language.isBlank()) {
      return language.trim().toLowerCase(Locale.ROOT);
    }
    return enterpriseRepository
        .findById(tenantId)
        .map(CrmTenantEnterpriseEntity::getPreferredLanguage)
        .orElse("en");
  }

  private static String localize(String english, String lang) {
    if (lang == null || lang.startsWith("en")) {
      return english;
    }
    if (lang.startsWith("hi")) {
      return "[hi] " + english;
    }
    if (lang.startsWith("ta")) {
      return "[ta] " + english;
    }
    if (lang.startsWith("te")) {
      return "[te] " + english;
    }
    return "[" + lang + "] " + english;
  }

  private static String extractAfter(String raw, String label) {
    if (raw == null) {
      return null;
    }
    int idx = raw.toLowerCase(Locale.ROOT).indexOf(label.toLowerCase(Locale.ROOT));
    if (idx < 0) {
      return null;
    }
    String rest = raw.substring(idx + label.length()).trim();
    int end = rest.indexOf('\n');
    return (end < 0 ? rest : rest.substring(0, end)).trim();
  }

  private static BigDecimal confidence(double v) {
    return BigDecimal.valueOf(Math.max(0, Math.min(1, v))).setScale(2, RoundingMode.HALF_UP);
  }

  private static String nvl(String v, String d) {
    return v == null || v.isBlank() ? d : v;
  }

  private static Map<String, Object> toInsight(CrmAiInsightEntity e) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", e.getId());
    m.put("relatedType", e.getRelatedType());
    m.put("relatedId", e.getRelatedId());
    m.put("insightType", e.getInsightType());
    m.put("title", e.getTitle());
    m.put("body", e.getBody());
    m.put("confidence", e.getConfidence());
    m.put("modelCode", e.getModelCode());
    m.put("languageCode", e.getLanguageCode());
    m.put("payload", e.getPayloadJson());
    m.put("createdAt", e.getCreatedAt());
    return m;
  }
}
