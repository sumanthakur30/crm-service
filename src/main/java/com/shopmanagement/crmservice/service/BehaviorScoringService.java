package com.shopmanagement.crmservice.service;

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
import com.shopmanagement.crmservice.persistence.entity.CrmScoreEventEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmScoreRuleEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmLeadRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmScoreEventRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmScoreRuleRepository;
import com.shopmanagement.crmservice.support.TenantIds;

@Service
public class BehaviorScoringService {

  private final CrmScoreRuleRepository ruleRepository;
  private final CrmScoreEventRepository eventRepository;
  private final CrmLeadRepository leadRepository;
  private final CrmLeadService leadService;
  private final TimelineService timelineService;

  public BehaviorScoringService(
      CrmScoreRuleRepository ruleRepository,
      CrmScoreEventRepository eventRepository,
      CrmLeadRepository leadRepository,
      CrmLeadService leadService,
      TimelineService timelineService) {
    this.ruleRepository = ruleRepository;
    this.eventRepository = eventRepository;
    this.leadRepository = leadRepository;
    this.leadService = leadService;
    this.timelineService = timelineService;
  }

  @Transactional
  public List<Map<String, Object>> ensureDefaultRules() {
    String tenantId = TenantIds.require();
    seed(tenantId, "EMAIL_PRESENT", "Email captured", "LEAD_FIELD", 10);
    seed(tenantId, "PHONE_PRESENT", "Phone captured", "LEAD_FIELD", 10);
    seed(tenantId, "NOTE_ADDED", "Note added", "NOTE_ADDED", 5);
    seed(tenantId, "CALL_CONNECTED", "Call connected", "CALL_CONNECTED", 15);
    seed(tenantId, "QUOTE_SENT", "Quote sent", "QUOTE_SENT", 20);
    seed(tenantId, "MEETING_BOOKED", "Meeting booked", "MEETING_BOOKED", 25);
    seed(tenantId, "CAMPAIGN_CAPTURE", "Campaign capture", "CAMPAIGN_CAPTURE", 8);
    return listRules();
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listRules() {
    return ruleRepository.findByTenantIdAndDeletedAtIsNull(TenantIds.require()).stream()
        .map(BehaviorScoringService::toRule)
        .toList();
  }

  @Transactional
  public Map<String, Object> upsertRule(Map<String, Object> body) {
    String tenantId = TenantIds.require();
    Long id = body.get("id") == null ? null : Long.valueOf(String.valueOf(body.get("id")));
    CrmScoreRuleEntity rule;
    if (id != null) {
      rule =
          ruleRepository
              .findByTenantIdAndIdAndDeletedAtIsNull(tenantId, id)
              .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rule not found"));
    } else {
      String code = String.valueOf(body.getOrDefault("code", "")).trim().toUpperCase(Locale.ROOT);
      if (code.isBlank()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "code is required");
      }
      rule =
          ruleRepository
              .findByTenantIdAndCodeAndDeletedAtIsNull(tenantId, code)
              .orElseGet(
                  () -> {
                    CrmScoreRuleEntity created = new CrmScoreRuleEntity();
                    created.setTenantId(tenantId);
                    created.setCode(code);
                    return created;
                  });
    }
    if (body.get("name") != null) {
      String name = String.valueOf(body.get("name")).trim();
      if (!name.isBlank()) {
        rule.setName(name);
      }
    }
    if (rule.getName() == null || rule.getName().isBlank()) {
      rule.setName(rule.getCode());
    }
    if (body.get("eventType") != null) {
      String eventType = String.valueOf(body.get("eventType")).trim().toUpperCase(Locale.ROOT);
      if (!eventType.isBlank()) {
        rule.setEventType(eventType);
      }
    }
    if (rule.getEventType() == null || rule.getEventType().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "eventType is required");
    }
    if (body.get("points") != null) {
      rule.setPoints(Integer.parseInt(String.valueOf(body.get("points"))));
    }
    if (body.get("active") != null) {
      rule.setActive(Boolean.parseBoolean(String.valueOf(body.get("active"))));
    }
    if (body.get("conditionJson") instanceof Map<?, ?> m) {
      Map<String, Object> cond = new LinkedHashMap<>();
      for (Map.Entry<?, ?> e : m.entrySet()) {
        cond.put(String.valueOf(e.getKey()), e.getValue());
      }
      rule.setConditionJson(cond);
    } else if (rule.getConditionJson() == null) {
      rule.setConditionJson(new LinkedHashMap<>());
    }
    rule.touch();
    return toRule(ruleRepository.save(rule));
  }

  @Transactional
  public LeadResponse applyEvent(Long leadId, String eventType, String summary, Map<String, Object> payload) {
    String tenantId = TenantIds.require();
    String type = eventType.trim().toUpperCase(Locale.ROOT);
    CrmLeadEntity lead =
        leadRepository
            .findByTenantIdAndIdAndDeletedAtIsNull(tenantId, leadId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead not found"));

    List<CrmScoreRuleEntity> rules =
        ruleRepository.findByTenantIdAndEventTypeAndActiveTrueAndDeletedAtIsNull(tenantId, type);
    if (rules.isEmpty()) {
      ensureDefaultRules();
      rules = ruleRepository.findByTenantIdAndEventTypeAndActiveTrueAndDeletedAtIsNull(tenantId, type);
    }

    int delta = 0;
    for (CrmScoreRuleEntity rule : rules) {
      CrmScoreEventEntity ev = new CrmScoreEventEntity();
      ev.setTenantId(tenantId);
      ev.setLeadId(leadId);
      ev.setRuleId(rule.getId());
      ev.setEventType(type);
      ev.setPoints(rule.getPoints());
      ev.setSummary(summary != null ? summary : rule.getName());
      ev.setPayloadJson(payload == null ? Map.of() : new LinkedHashMap<>(payload));
      eventRepository.save(ev);
      delta += rule.getPoints();
    }

    if (delta != 0) {
      int next = Math.max(0, Math.min(100, lead.getScore() + delta));
      lead.setScore(next);
      lead.touch();
      leadRepository.save(lead);
      timelineService.recordEvent(
          "LEAD",
          leadId,
          "SCORE_CHANGED",
          "Score +" + delta + " via " + type + " → " + next,
          Map.of("eventType", type, "delta", delta, "score", next));
    }
    return leadService.get(leadId);
  }

  @Transactional
  public LeadResponse rescore(Long leadId) {
    String tenantId = TenantIds.require();
    CrmLeadEntity lead =
        leadRepository
            .findByTenantIdAndIdAndDeletedAtIsNull(tenantId, leadId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead not found"));
    int sum = eventRepository.sumPointsByTenantIdAndLeadId(tenantId, leadId);
    int score = Math.max(0, Math.min(100, sum));
    lead.setScore(score);
    lead.touch();
    leadRepository.save(lead);
    return leadService.get(leadId);
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> eventsForLead(Long leadId) {
    return eventRepository
        .findByTenantIdAndLeadIdOrderByCreatedAtDesc(TenantIds.require(), leadId)
        .stream()
        .map(
            e -> {
              Map<String, Object> m = new LinkedHashMap<>();
              m.put("id", e.getId());
              m.put("eventType", e.getEventType());
              m.put("points", e.getPoints());
              m.put("summary", e.getSummary());
              m.put("createdAt", e.getCreatedAt());
              return m;
            })
        .toList();
  }

  private void seed(String tenantId, String code, String name, String eventType, int points) {
    ruleRepository
        .findByTenantIdAndCodeAndDeletedAtIsNull(tenantId, code)
        .orElseGet(
            () -> {
              CrmScoreRuleEntity r = new CrmScoreRuleEntity();
              r.setTenantId(tenantId);
              r.setCode(code);
              r.setName(name);
              r.setEventType(eventType);
              r.setPoints(points);
              r.setActive(true);
              return ruleRepository.save(r);
            });
  }

  private static Map<String, Object> toRule(CrmScoreRuleEntity r) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", r.getId());
    m.put("code", r.getCode());
    m.put("name", r.getName());
    m.put("eventType", r.getEventType());
    m.put("points", r.getPoints());
    m.put("active", r.isActive());
    m.put("conditionJson", r.getConditionJson() == null ? Map.of() : r.getConditionJson());
    return m;
  }
}
