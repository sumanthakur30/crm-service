package com.shopmanagement.crmservice.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopmanagement.crmservice.persistence.entity.CrmCallLogEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmCalendarEventEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmNoteEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmTaskEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmCallLogRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmCalendarEventRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmNoteRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmTaskRepository;
import com.shopmanagement.crmservice.support.TenantIds;

/** Sprint 4 — unified read facade over tasks, meetings, calls, and recent notes. */
@Service
public class ActivityService {

  private final CrmTaskRepository taskRepository;
  private final CrmCalendarEventRepository calendarRepository;
  private final CrmCallLogRepository callLogRepository;
  private final CrmNoteRepository noteRepository;

  public ActivityService(
      CrmTaskRepository taskRepository,
      CrmCalendarEventRepository calendarRepository,
      CrmCallLogRepository callLogRepository,
      CrmNoteRepository noteRepository) {
    this.taskRepository = taskRepository;
    this.calendarRepository = calendarRepository;
    this.callLogRepository = callLogRepository;
    this.noteRepository = noteRepository;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> list(Instant from, Instant to, String typesCsv, int limit) {
    String tenantId = TenantIds.require();
    Instant start = from == null ? Instant.now().minus(7, ChronoUnit.DAYS) : from;
    Instant end = to == null ? Instant.now().plus(7, ChronoUnit.DAYS) : to;
    int cap = Math.max(1, Math.min(limit <= 0 ? 100 : limit, 200));
    Set<String> types = parseTypes(typesCsv);

    List<Map<String, Object>> rows = new ArrayList<>();
    if (types.contains("TASK")) {
      for (CrmTaskEntity t :
          taskRepository.findByTenantIdAndStatusAndDeletedAtIsNullOrderByDueAtAsc(tenantId, "OPEN")) {
        Instant due = t.getDueAt();
        if (due == null || due.isBefore(start) || !due.isBefore(end)) {
          continue;
        }
        rows.add(
            activity(
                "TASK",
                t.getId(),
                t.getTitle(),
                t.getRelatedType(),
                t.getRelatedId(),
                t.getOwnerUserId(),
                due,
                t.getStatus(),
                t.getSourceCode()));
      }
    }
    if (types.contains("MEETING")) {
      for (CrmCalendarEventEntity e :
          calendarRepository
              .findByTenantIdAndStartsAtGreaterThanEqualAndStartsAtLessThanAndDeletedAtIsNullOrderByStartsAtAsc(
                  tenantId, start, end)) {
        rows.add(
            activity(
                "MEETING",
                e.getId(),
                e.getTitle(),
                e.getRelatedType(),
                e.getRelatedId(),
                e.getOwnerUserId(),
                e.getStartsAt(),
                e.getStatus(),
                "CALENDAR"));
      }
    }
    if (types.contains("CALL")) {
      for (CrmCallLogEntity c :
          callLogRepository.findByTenantIdAndOccurredAtGreaterThanEqualOrderByOccurredAtDesc(
              tenantId, start)) {
        Instant when = c.getOccurredAt();
        if (when == null || !when.isBefore(end)) {
          continue;
        }
        String title =
            (c.getDirection() == null ? "Call" : c.getDirection())
                + (c.getOutcome() == null ? "" : " · " + c.getOutcome());
        rows.add(
            activity(
                "CALL",
                c.getId(),
                title,
                "LEAD",
                c.getLeadId(),
                null,
                when,
                c.getOutcome(),
                "CALL_LOG"));
      }
    }
    if (types.contains("NOTE")) {
      for (CrmNoteEntity n :
          noteRepository.findByTenantIdAndCreatedAtGreaterThanEqualAndDeletedAtIsNullOrderByCreatedAtDesc(
              tenantId, start)) {
        Instant when = n.getCreatedAt();
        if (when == null || !when.isBefore(end)) {
          continue;
        }
        String body = n.getBody() == null ? "" : n.getBody().trim();
        String title = body.length() > 80 ? body.substring(0, 77) + "…" : body;
        rows.add(
            activity(
                "NOTE",
                n.getId(),
                title.isBlank() ? "Note" : title,
                n.getRelatedType(),
                n.getRelatedId(),
                n.getAuthorUserId(),
                when,
                "CREATED",
                "NOTE"));
      }
    }

    rows.sort(
        Comparator.comparing(
            (Map<String, Object> r) -> (Instant) r.get("occurredAt"),
            Comparator.nullsLast(Comparator.reverseOrder())));
    if (rows.size() > cap) {
      return rows.subList(0, cap);
    }
    return rows;
  }

  private static Map<String, Object> activity(
      String type,
      Long id,
      String title,
      String relatedType,
      Long relatedId,
      String ownerUserId,
      Instant occurredAt,
      String status,
      String source) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("type", type);
    m.put("id", id);
    m.put("title", title);
    m.put("relatedType", relatedType);
    m.put("relatedId", relatedId);
    m.put("ownerUserId", ownerUserId);
    m.put("occurredAt", occurredAt);
    m.put("status", status);
    m.put("source", source);
    return m;
  }

  static Set<String> parseTypes(String typesCsv) {
    if (typesCsv == null || typesCsv.isBlank()) {
      return Set.of("TASK", "MEETING", "CALL", "NOTE");
    }
    Set<String> out = new java.util.LinkedHashSet<>();
    for (String part : typesCsv.split(",")) {
      String t = part.trim().toUpperCase(Locale.ROOT);
      if (!t.isEmpty()) {
        out.add(t);
      }
    }
    return out.isEmpty() ? Set.of("TASK", "MEETING", "CALL", "NOTE") : out;
  }
}
