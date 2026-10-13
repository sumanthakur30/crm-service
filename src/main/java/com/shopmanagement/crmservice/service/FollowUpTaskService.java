package com.shopmanagement.crmservice.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopmanagement.crmservice.persistence.entity.CrmLeadEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmOpportunityEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmTaskEntity;
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

  private final CrmTaskRepository taskRepository;

  public FollowUpTaskService(CrmTaskRepository taskRepository) {
    this.taskRepository = taskRepository;
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

  /** Last second of the follow-up date in the same zone My Day uses, so the task is due that day. */
  static Instant dueAtEndOfDay(LocalDate date) {
    return date.plusDays(1).atStartOfDay(MyDayService.ZONE).toInstant().minusSeconds(1);
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
