package com.shopmanagement.crmservice.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopmanagement.crmservice.persistence.entity.CrmLeadEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmTaskEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmTaskRepository;

/**
 * Keeps one open follow-up task per lead. A later save moves that task; it does not add another.
 */
@Service
public class FollowUpTaskService {

  public static final String SOURCE_LEAD_FOLLOW_UP = "LEAD_FOLLOW_UP";
  static final String RELATED_LEAD = "LEAD";

  private final CrmTaskRepository taskRepository;

  public FollowUpTaskService(CrmTaskRepository taskRepository) {
    this.taskRepository = taskRepository;
  }

  @Transactional
  public void syncLead(CrmLeadEntity lead) {
    if (lead == null || lead.getId() == null) {
      return;
    }
    String raw = readNextFollowUp(lead.getAttributes());
    List<CrmTaskEntity> open =
        taskRepository
            .findByTenantIdAndRelatedTypeAndRelatedIdAndSourceCodeAndStatusAndDeletedAtIsNullOrderByIdAsc(
                lead.getTenantId(), RELATED_LEAD, lead.getId(), SOURCE_LEAD_FOLLOW_UP, "OPEN");
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
    String title = followUpTitle(lead.getTitle());
    String priority = taskPriority(lead.getPriority());
    if (open.isEmpty()) {
      CrmTaskEntity task = new CrmTaskEntity();
      task.setTenantId(lead.getTenantId());
      task.setRelatedType(RELATED_LEAD);
      task.setRelatedId(lead.getId());
      task.setSourceCode(SOURCE_LEAD_FOLLOW_UP);
      task.setStatus("OPEN");
      task.setDueAt(dueAt);
      task.setTitle(title);
      task.setPriority(priority);
      task.setOwnerUserId(lead.getOwnerUserId());
      taskRepository.save(task);
      return;
    }
    CrmTaskEntity keep = open.get(0);
    if (!dueAt.equals(keep.getDueAt())
        || !title.equals(keep.getTitle())
        || !priority.equals(keep.getPriority())
        || !sameOwner(keep.getOwnerUserId(), lead.getOwnerUserId())) {
      keep.setDueAt(dueAt);
      keep.setTitle(title);
      keep.setPriority(priority);
      keep.setOwnerUserId(lead.getOwnerUserId());
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

  private static String followUpTitle(String leadTitle) {
    String name = leadTitle == null || leadTitle.isBlank() ? "lead" : leadTitle.trim();
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
