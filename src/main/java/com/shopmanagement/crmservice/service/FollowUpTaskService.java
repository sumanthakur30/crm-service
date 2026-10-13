package com.shopmanagement.crmservice.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopmanagement.crmservice.persistence.entity.CrmLeadEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmOpportunityEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmTaskEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmLeadRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmOpportunityRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmTaskRepository;

/**
 * Keeps one open follow-up task per lead or deal. A later save moves that task; it does not add another.
 */
@Service
public class FollowUpTaskService {

  public static final String SOURCE_LEAD_FOLLOW_UP = "LEAD_FOLLOW_UP";
  public static final String SOURCE_OPPORTUNITY_FOLLOW_UP = "OPPORTUNITY_FOLLOW_UP";
  static final String RELATED_LEAD = "LEAD";
  static final String RELATED_OPPORTUNITY = "OPPORTUNITY";
  static final String EVENT_FOLLOW_UP_COMPLETED = "FOLLOW_UP_COMPLETED";

  private final CrmTaskRepository taskRepository;
  private final CrmLeadRepository leadRepository;
  private final CrmOpportunityRepository opportunityRepository;
  private final TimelineService timelineService;

  public FollowUpTaskService(
      CrmTaskRepository taskRepository,
      CrmLeadRepository leadRepository,
      CrmOpportunityRepository opportunityRepository,
      TimelineService timelineService) {
    this.taskRepository = taskRepository;
    this.leadRepository = leadRepository;
    this.opportunityRepository = opportunityRepository;
    this.timelineService = timelineService;
  }

  @Transactional
  public void syncLead(CrmLeadEntity lead) {
    if (lead == null || lead.getId() == null) {
      return;
    }
    sync(
        lead.getTenantId(),
        RELATED_LEAD,
        lead.getId(),
        SOURCE_LEAD_FOLLOW_UP,
        followUpTitle(lead.getTitle(), "lead"),
        taskPriority(lead.getPriority()),
        lead.getOwnerUserId(),
        lead.getAttributes());
  }

  @Transactional
  public void syncOpportunity(CrmOpportunityEntity opportunity) {
    if (opportunity == null || opportunity.getId() == null) {
      return;
    }
    sync(
        opportunity.getTenantId(),
        RELATED_OPPORTUNITY,
        opportunity.getId(),
        SOURCE_OPPORTUNITY_FOLLOW_UP,
        followUpTitle(opportunity.getName(), "deal"),
        "MEDIUM",
        opportunity.getOwnerUserId(),
        opportunity.getAttributes());
  }

  private void sync(
      String tenantId,
      String relatedType,
      Long relatedId,
      String sourceCode,
      String title,
      String priority,
      String ownerUserId,
      Map<String, Object> attributes) {
    List<CrmTaskEntity> open =
        taskRepository
            .findByTenantIdAndRelatedTypeAndRelatedIdAndSourceCodeAndStatusAndDeletedAtIsNullOrderByIdAsc(
                tenantId, relatedType, relatedId, sourceCode, "OPEN");
    String raw = readNextFollowUp(attributes);
    if (raw == null) {
      cancelAll(open);
      return;
    }
    LocalDate date;
    try {
      date = LocalDate.parse(raw);
    } catch (DateTimeParseException ex) {
      return;
    }
    Instant dueAt = dueAtEndOfDay(date);
    if (open.isEmpty()) {
      CrmTaskEntity task = new CrmTaskEntity();
      task.setTenantId(tenantId);
      task.setRelatedType(relatedType);
      task.setRelatedId(relatedId);
      task.setSourceCode(sourceCode);
      task.setStatus("OPEN");
      task.setDueAt(dueAt);
      task.setTitle(title);
      task.setPriority(priority);
      task.setOwnerUserId(ownerUserId);
      taskRepository.save(task);
      return;
    }
    CrmTaskEntity keep = open.get(0);
    if (!dueAt.equals(keep.getDueAt())
        || !title.equals(keep.getTitle())
        || !priority.equals(keep.getPriority())
        || !sameOwner(keep.getOwnerUserId(), ownerUserId)) {
      keep.setDueAt(dueAt);
      keep.setTitle(title);
      keep.setPriority(priority);
      keep.setOwnerUserId(ownerUserId);
      keep.touch();
      taskRepository.save(keep);
    }
    cancelAll(open.subList(1, open.size()));
  }

  /**
   * A completed lead or deal follow-up drops the saved next date so a later save does not open the
   * same task again. Qualification and the other profile fields stay. The completed day is stored
   * beside the profile so a later profile edit does not erase it.
   */
  @Transactional
  public void completeFollowUp(CrmTaskEntity task) {
    if (task == null || task.getRelatedId() == null || task.getTenantId() == null) {
      return;
    }
    if (SOURCE_LEAD_FOLLOW_UP.equals(task.getSourceCode())) {
      leadRepository
          .findByTenantIdAndIdAndDeletedAtIsNull(task.getTenantId(), task.getRelatedId())
          .ifPresent(lead -> finish(lead.getAttributes(), lead::setAttributes, lead::touch, () -> leadRepository.save(lead), RELATED_LEAD, lead.getId(), task.getId()));
      return;
    }
    if (SOURCE_OPPORTUNITY_FOLLOW_UP.equals(task.getSourceCode())) {
      opportunityRepository
          .findByTenantIdAndIdAndDeletedAtIsNull(task.getTenantId(), task.getRelatedId())
          .ifPresent(
              deal ->
                  finish(
                      deal.getAttributes(),
                      deal::setAttributes,
                      deal::touch,
                      () -> opportunityRepository.save(deal),
                      RELATED_OPPORTUNITY,
                      deal.getId(),
                      task.getId()));
    }
  }

  private void finish(
      Map<String, Object> attributes,
      java.util.function.Consumer<Map<String, Object>> writeAttributes,
      Runnable touch,
      Runnable save,
      String relatedType,
      Long relatedId,
      Long taskId) {
    writeAttributes.accept(clearedFollowUp(attributes));
    touch.run();
    save.run();
    timelineService.recordEvent(
        relatedType,
        relatedId,
        EVENT_FOLLOW_UP_COMPLETED,
        "Follow-up completed",
        Map.of("taskId", taskId));
  }

  /** Last second of the follow-up date in the same zone My Day uses, so the task is due that day. */
  static Instant dueAtEndOfDay(LocalDate date) {
    return date.plusDays(1).atStartOfDay(MyDayService.ZONE).toInstant().minusSeconds(1);
  }

  static Map<String, Object> clearedFollowUp(Map<String, Object> attributes) {
    Map<String, Object> copy = new LinkedHashMap<>();
    if (attributes != null) {
      copy.putAll(attributes);
    }
    Object profile = copy.get("profile");
    if (profile instanceof Map<?, ?> raw) {
      Map<String, Object> profileCopy = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : raw.entrySet()) {
        if (!"nextFollowUpDate".equals(String.valueOf(entry.getKey()))) {
          profileCopy.put(String.valueOf(entry.getKey()), entry.getValue());
        }
      }
      copy.put("profile", profileCopy);
    }
    copy.put("lastFollowUpOn", LocalDate.now(MyDayService.ZONE).toString());
    return copy;
  }

  private static String readNextFollowUp(Map<String, Object> attributes) {
    if (attributes == null) {
      return null;
    }
    Object profile = attributes.get("profile");
    if (!(profile instanceof Map<?, ?> map)) {
      return null;
    }
    Object value = map.get("nextFollowUpDate");
    if (!(value instanceof String text)) {
      return null;
    }
    String trimmed = text.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static String followUpTitle(String recordTitle, String fallback) {
    String name = recordTitle == null || recordTitle.isBlank() ? fallback : recordTitle.trim();
    String title = "Follow up: " + name;
    return title.length() <= 256 ? title : title.substring(0, 256);
  }

  private static String taskPriority(String leadPriority) {
    if (leadPriority == null || leadPriority.isBlank()) {
      return "MEDIUM";
    }
    String value = leadPriority.trim().toUpperCase();
    return value.length() <= 16 ? value : "MEDIUM";
  }

  private static boolean sameOwner(String current, String next) {
    if (current == null) {
      return next == null;
    }
    return current.equals(next);
  }

  private void cancelAll(List<CrmTaskEntity> tasks) {
    for (CrmTaskEntity task : tasks) {
      task.setStatus("CANCELLED");
      task.touch();
      taskRepository.save(task);
    }
  }
}
