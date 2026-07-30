package com.shopmanagement.crmservice.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.shopmanagement.crmservice.integration.RuleEngineClient;
import com.shopmanagement.crmservice.persistence.entity.CrmStageAutomationEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmStageEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmTaskEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmStageAutomationRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmStageRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmTaskRepository;
import com.shopmanagement.crmservice.support.TenantIds;

/**
 * Runs CRM-local stage automation rules on lead/deal stage changes, and optionally asks School
 * rule-engine for matched action codes (CREATE_TASK / TIMELINE_NOTE).
 */
@Service
public class StageAutomationService {

  private final CrmStageAutomationRepository automationRepository;
  private final CrmStageRepository stageRepository;
  private final CrmTaskRepository taskRepository;
  private final TimelineService timelineService;
  private final RuleEngineClient ruleEngineClient;

  public StageAutomationService(
      CrmStageAutomationRepository automationRepository,
      CrmStageRepository stageRepository,
      CrmTaskRepository taskRepository,
      TimelineService timelineService,
      RuleEngineClient ruleEngineClient) {
    this.automationRepository = automationRepository;
    this.stageRepository = stageRepository;
    this.taskRepository = taskRepository;
    this.timelineService = timelineService;
    this.ruleEngineClient = ruleEngineClient;
  }

  @Transactional
  public List<Map<String, Object>> listRules() {
    String tenantId = TenantIds.require();
    ensureDefaults(tenantId);
    return automationRepository
        .findByTenantIdAndDeletedAtIsNullOrderByObjectTypeAscSortOrderAscIdAsc(tenantId)
        .stream()
        .map(StageAutomationService::toMap)
        .toList();
  }

  @Transactional
  public Map<String, Object> createRule(Map<String, Object> body) {
    String tenantId = TenantIds.require();
    CrmStageAutomationEntity e = new CrmStageAutomationEntity();
    e.setTenantId(tenantId);
    e.setObjectType(req(body, "objectType").toUpperCase(Locale.ROOT));
    if (!"LEAD".equals(e.getObjectType()) && !"OPPORTUNITY".equals(e.getObjectType())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "objectType must be LEAD or OPPORTUNITY");
    }
    e.setToStageCode(blankToNull(str(body.get("toStageCode"))));
    e.setToStageId(asLong(body.get("toStageId")));
    String action = req(body, "actionType").toUpperCase(Locale.ROOT);
    if (!"CREATE_TASK".equals(action) && !"TIMELINE_NOTE".equals(action)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "actionType must be CREATE_TASK or TIMELINE_NOTE");
    }
    e.setActionType(action);
    @SuppressWarnings("unchecked")
    Map<String, Object> config =
        body.get("actionConfig") instanceof Map<?, ?> m
            ? new LinkedHashMap<>((Map<String, Object>) m)
            : new LinkedHashMap<>();
    e.setActionConfig(config);
    e.setActive(body.get("active") == null || Boolean.TRUE.equals(body.get("active")));
    e.setSortOrder(body.get("sortOrder") instanceof Number n ? n.intValue() : 100);
    return toMap(automationRepository.save(e));
  }

  /**
   * Fire matching local rules (+ optional rule-engine) after a stage change. Failures are logged via
   * timeline only — never rolls back the stage move.
   */
  @Transactional
  public void onStageChanged(
      String objectType,
      Long objectId,
      Long fromStageId,
      Long toStageId,
      String ownerUserId,
      String displayName) {
    if (toStageId == null || Objects.equals(fromStageId, toStageId)) {
      return;
    }
    String tenantId = TenantIds.require();
    ensureDefaults(tenantId);
    CrmStageEntity toStage =
        stageRepository.findByTenantIdAndIdAndDeletedAtIsNull(tenantId, toStageId).orElse(null);
    String toCode = toStage == null ? null : toStage.getCode();
    String toName = toStage == null ? String.valueOf(toStageId) : toStage.getName();

    List<Map<String, Object>> fired = new ArrayList<>();
    for (CrmStageAutomationEntity rule :
        automationRepository.findByTenantIdAndObjectTypeAndActiveTrueAndDeletedAtIsNullOrderBySortOrderAscIdAsc(
            tenantId, objectType.toUpperCase(Locale.ROOT))) {
      if (!matches(rule, toStageId, toCode)) {
        continue;
      }
      Map<String, Object> result =
          runAction(rule.getActionType(), rule.getActionConfig(), objectType, objectId, ownerUserId, displayName, toName);
      result.put("ruleId", rule.getId());
      fired.add(result);
    }

    if (ruleEngineClient.isEnabled()) {
      Map<String, Object> ctx = new LinkedHashMap<>();
      ctx.put("crm.objectType", objectType);
      ctx.put("crm.objectId", objectId);
      ctx.put("crm.fromStageId", fromStageId);
      ctx.put("crm.toStageId", toStageId);
      ctx.put("crm.toStageCode", toCode);
      ctx.put("crm.event", "STAGE_CHANGED");
      List<String> matched = ruleEngineClient.evaluateMatchedActions(tenantId, ctx);
      for (String action : matched) {
        if ("CREATE_TASK".equalsIgnoreCase(action) || "NOTIFY_CRM_TASK".equalsIgnoreCase(action)) {
          Map<String, Object> cfg = Map.of("title", "Rule-engine follow-up: " + displayName + " → " + toName);
          fired.add(runAction("CREATE_TASK", cfg, objectType, objectId, ownerUserId, displayName, toName));
        } else if ("TIMELINE_NOTE".equalsIgnoreCase(action) || action.toUpperCase(Locale.ROOT).startsWith("NOTIFY_")) {
          Map<String, Object> cfg = Map.of("summary", "Rule-engine matched: " + action);
          fired.add(runAction("TIMELINE_NOTE", cfg, objectType, objectId, ownerUserId, displayName, toName));
        }
      }
    }

    if (!fired.isEmpty()) {
      timelineService.recordEvent(
          objectType,
          objectId,
          "STAGE_AUTOMATION",
          "Stage automation ran (" + fired.size() + " action(s)) on " + toName,
          Map.of("actions", fired, "toStageId", toStageId, "toStageCode", toCode == null ? "" : toCode));
    }
  }

  private Map<String, Object> runAction(
      String actionType,
      Map<String, Object> config,
      String objectType,
      Long objectId,
      String ownerUserId,
      String displayName,
      String stageName) {
    Map<String, Object> cfg = config == null ? Map.of() : config;
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("actionType", actionType);
    if ("CREATE_TASK".equalsIgnoreCase(actionType)) {
      String title =
          Objects.toString(
              cfg.get("title"),
              "Follow up · " + displayName + " moved to " + stageName);
      int dueHours = cfg.get("dueHours") instanceof Number n ? n.intValue() : 24;
      String priority = Objects.toString(cfg.get("priority"), "MEDIUM");
      CrmTaskEntity task = new CrmTaskEntity();
      task.setTenantId(TenantIds.require());
      task.setRelatedType(objectType.toUpperCase(Locale.ROOT));
      task.setRelatedId(objectId);
      task.setTitle(title);
      task.setStatus("OPEN");
      task.setPriority(priority);
      task.setDueAt(Instant.now().plus(dueHours, ChronoUnit.HOURS));
      task.setOwnerUserId(ownerUserId);
      task.setSourceCode("STAGE_AUTOMATION");
      task = taskRepository.save(task);
      out.put("taskId", task.getId());
      out.put("title", title);
    } else if ("TIMELINE_NOTE".equalsIgnoreCase(actionType)) {
      String summary =
          Objects.toString(cfg.get("summary"), "Auto-note: moved to " + stageName);
      timelineService.recordEvent(
          objectType,
          objectId,
          "AUTOMATION_NOTE",
          summary,
          Map.of("stageName", stageName));
      out.put("summary", summary);
    }
    return out;
  }

  private static boolean matches(CrmStageAutomationEntity rule, Long toStageId, String toCode) {
    if (rule.getToStageId() != null) {
      return rule.getToStageId().equals(toStageId);
    }
    if (rule.getToStageCode() != null && !rule.getToStageCode().isBlank()) {
      return toCode != null && rule.getToStageCode().equalsIgnoreCase(toCode);
    }
    // No target filter = fire on every stage change for that object type (rare; skip).
    return false;
  }

  private void ensureDefaults(String tenantId) {
    if (automationRepository.countByTenantIdAndDeletedAtIsNull(tenantId) > 0) {
      return;
    }
    seed(tenantId, "LEAD", "QUALIFIED", "CREATE_TASK", Map.of("title", "Schedule discovery call", "dueHours", 24, "priority", "HIGH"), 10);
    seed(tenantId, "LEAD", "DEMO", "CREATE_TASK", Map.of("title", "Complete demo follow-up", "dueHours", 24, "priority", "HIGH"), 15);
    seed(tenantId, "LEAD", "QUOTE", "CREATE_TASK", Map.of("title", "Issue retail quotation", "dueHours", 24), 20);
    seed(tenantId, "LEAD", "PROPOSAL", "CREATE_TASK", Map.of("title", "Prepare proposal pack", "dueHours", 48), 25);
    seed(tenantId, "OPPORTUNITY", "NEGOTIATION", "CREATE_TASK", Map.of("title", "Prepare quotation / discount check", "dueHours", 24, "priority", "HIGH"), 10);
    seed(tenantId, "OPPORTUNITY", "PROPOSAL", "TIMELINE_NOTE", Map.of("summary", "Deal entered Proposal — confirm stakeholder map"), 20);
    seed(tenantId, "OPPORTUNITY", "WON", "TIMELINE_NOTE", Map.of("summary", "Deal won — kick off onboarding checklist"), 30);
    seed(tenantId, "OPPORTUNITY", "CLOSED", "TIMELINE_NOTE", Map.of("summary", "Retail deal closed won — handoff to fulfillment"), 35);
    seed(tenantId, "OPPORTUNITY", "SOLD", "TIMELINE_NOTE", Map.of("summary", "Sold — confirm delivery / invoice"), 40);
  }

  private void seed(
      String tenantId,
      String objectType,
      String stageCode,
      String actionType,
      Map<String, Object> config,
      int sort) {
    CrmStageAutomationEntity e = new CrmStageAutomationEntity();
    e.setTenantId(tenantId);
    e.setObjectType(objectType);
    e.setToStageCode(stageCode);
    e.setActionType(actionType);
    e.setActionConfig(new LinkedHashMap<>(config));
    e.setActive(true);
    e.setSortOrder(sort);
    automationRepository.save(e);
  }

  private static Map<String, Object> toMap(CrmStageAutomationEntity e) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", e.getId());
    m.put("objectType", e.getObjectType());
    m.put("toStageCode", e.getToStageCode());
    m.put("toStageId", e.getToStageId());
    m.put("actionType", e.getActionType());
    m.put("actionConfig", e.getActionConfig());
    m.put("active", e.isActive());
    m.put("sortOrder", e.getSortOrder());
    return m;
  }

  private static String req(Map<String, Object> body, String key) {
    String v = str(body.get(key));
    if (v == null || v.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " is required");
    }
    return v.trim();
  }

  private static String str(Object v) {
    return v == null ? null : String.valueOf(v);
  }

  private static String blankToNull(String v) {
    return v == null || v.isBlank() ? null : v.trim();
  }

  private static Long asLong(Object v) {
    if (v == null) {
      return null;
    }
    if (v instanceof Number n) {
      return n.longValue();
    }
    String s = String.valueOf(v).trim();
    if (s.isEmpty()) {
      return null;
    }
    return Long.parseLong(s);
  }
}
