package com.shopmanagement.crmservice.service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopmanagement.crmservice.persistence.entity.CrmCalendarEventEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmLeadEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmTaskEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmCalendarEventRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmLeadRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmTaskRepository;
import com.shopmanagement.crmservice.support.TenantIds;

/** Sprint 4 — “What should I do today?” snapshot. */
@Service
public class MyDayService {

  public static final int DEFAULT_HOT_SCORE = 70;
  public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

  private final CrmTaskRepository taskRepository;
  private final CrmLeadRepository leadRepository;
  private final CrmCalendarEventRepository calendarRepository;
  private final OpsService opsService;
  private final ActivityService activityService;
  private final ScoreBandService scoreBandService;

  public MyDayService(
      CrmTaskRepository taskRepository,
      CrmLeadRepository leadRepository,
      CrmCalendarEventRepository calendarRepository,
      OpsService opsService,
      ActivityService activityService,
      ScoreBandService scoreBandService) {
    this.taskRepository = taskRepository;
    this.leadRepository = leadRepository;
    this.calendarRepository = calendarRepository;
    this.opsService = opsService;
    this.activityService = activityService;
    this.scoreBandService = scoreBandService;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> snapshot(Integer hotMinScore, Integer hotLimit) {
    String tenantId = TenantIds.require();
    Instant now = Instant.now();
    LocalDate today = LocalDate.now(ZONE);
    Instant dayStart = today.atStartOfDay(ZONE).toInstant();
    Instant dayEnd = today.plusDays(1).atStartOfDay(ZONE).toInstant();
    int minScore =
        hotMinScore == null
            ? scoreBandService.hotMin()
            : Math.max(0, hotMinScore);
    int leadCap = hotLimit == null ? 10 : Math.max(1, Math.min(hotLimit, 50));

    List<Map<String, Object>> overdue =
        taskRepository
            .findByTenantIdAndStatusAndDueAtBeforeAndDeletedAtIsNullOrderByDueAtAsc(
                tenantId, "OPEN", now)
            .stream()
            .map(MyDayService::toTask)
            .toList();

    List<Map<String, Object>> dueToday =
        taskRepository
            .findByTenantIdAndStatusAndDueAtGreaterThanEqualAndDueAtLessThanAndDeletedAtIsNullOrderByDueAtAsc(
                tenantId, "OPEN", dayStart, dayEnd)
            .stream()
            .map(MyDayService::toTask)
            .toList();

    List<Map<String, Object>> meetingsToday =
        calendarRepository
            .findByTenantIdAndStartsAtGreaterThanEqualAndStartsAtLessThanAndDeletedAtIsNullOrderByStartsAtAsc(
                tenantId, dayStart, dayEnd)
            .stream()
            .filter(MyDayService::openMeeting)
            .map(
                e -> {
                  Map<String, Object> m = new LinkedHashMap<>();
                  m.put("id", e.getId());
                  m.put("title", e.getTitle());
                  m.put("startsAt", e.getStartsAt());
                  m.put("endsAt", e.getEndsAt());
                  m.put("relatedType", e.getRelatedType());
                  m.put("relatedId", e.getRelatedId());
                  m.put("ownerUserId", e.getOwnerUserId());
                  m.put("status", e.getStatus());
                  return m;
                })
            .toList();

    List<Map<String, Object>> hotLeads =
        leadRepository
            .findByTenantIdAndStatusAndScoreGreaterThanEqualAndDeletedAtIsNullOrderByScoreDesc(
                tenantId, "OPEN", minScore, PageRequest.of(0, leadCap))
            .stream()
            .map(MyDayService::toHotLead)
            .toList();

    List<Map<String, Object>> openApprovals = opsService.listApprovals("PENDING");

    List<Map<String, Object>> recentActivities =
        activityService.list(now.minusSeconds(86400L * 2), now.plusSeconds(86400), null, 25);

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("asOf", now);
    out.put("timezone", ZONE.getId());
    out.put("overdueTasks", overdue);
    out.put("dueToday", dueToday);
    out.put("meetingsToday", meetingsToday);
    out.put("hotLeads", hotLeads);
    out.put("hotMinScore", minScore);
    out.put("openApprovals", openApprovals);
    out.put("recentActivities", recentActivities);
    out.put(
        "counts",
        Map.of(
            "overdueTasks",
            overdue.size(),
            "dueToday",
            dueToday.size(),
            "meetingsToday",
            meetingsToday.size(),
            "hotLeads",
            hotLeads.size(),
            "openApprovals",
            openApprovals.size()));
    return out;
  }

  /** Monday–Sunday week in {@link #ZONE}. Tasks and meetings are grouped by that local date. */
  @Transactional(readOnly = true)
  public Map<String, Object> week(LocalDate weekOf) {
    Map<String, Object> calendar = calendar("week", weekOf);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("timezone", calendar.get("timezone"));
    out.put("weekStart", calendar.get("rangeStart"));
    out.put("weekEnd", calendar.get("rangeEnd"));
    out.put("days", calendar.get("days"));
    return out;
  }

  /**
   * Day, week, or month of open tasks and meetings. A month grid starts on Monday and may include
   * days from the neighbouring months so the columns stay aligned.
   */
  @Transactional(readOnly = true)
  public Map<String, Object> calendar(String view, LocalDate anchorDate) {
    String tenantId = TenantIds.require();
    LocalDate anchor = anchorDate == null ? LocalDate.now(ZONE) : anchorDate;
    String mode = view == null ? "week" : view.trim().toLowerCase(java.util.Locale.ROOT);
    LocalDate rangeStart;
    LocalDate rangeEnd;
    LocalDate gridStart;
    LocalDate gridEnd;
    if ("day".equals(mode)) {
      rangeStart = anchor;
      rangeEnd = anchor;
      gridStart = anchor;
      gridEnd = anchor;
    } else if ("month".equals(mode)) {
      rangeStart = anchor.withDayOfMonth(1);
      rangeEnd = rangeStart.plusMonths(1).minusDays(1);
      gridStart = rangeStart.with(DayOfWeek.MONDAY);
      gridEnd = rangeEnd.with(DayOfWeek.SUNDAY);
    } else {
      mode = "week";
      rangeStart = anchor.with(DayOfWeek.MONDAY);
      rangeEnd = rangeStart.plusDays(6);
      gridStart = rangeStart;
      gridEnd = rangeEnd;
    }
    Instant from = gridStart.atStartOfDay(ZONE).toInstant();
    Instant to = gridEnd.plusDays(1).atStartOfDay(ZONE).toInstant();
    List<CrmTaskEntity> tasks =
        taskRepository
            .findByTenantIdAndStatusAndDueAtGreaterThanEqualAndDueAtLessThanAndDeletedAtIsNullOrderByDueAtAsc(
                tenantId, "OPEN", from, to);
    var meetings =
        calendarRepository
            .findByTenantIdAndStartsAtGreaterThanEqualAndStartsAtLessThanAndDeletedAtIsNullOrderByStartsAtAsc(
                tenantId, from, to);

    List<Map<String, Object>> days = new ArrayList<>();
    for (LocalDate date = gridStart; !date.isAfter(gridEnd); date = date.plusDays(1)) {
      List<Map<String, Object>> dayTasks = new ArrayList<>();
      for (CrmTaskEntity task : tasks) {
        if (task.getDueAt() != null && date.equals(task.getDueAt().atZone(ZONE).toLocalDate())) {
          dayTasks.add(toTask(task));
        }
      }
      List<Map<String, Object>> dayMeetings = new ArrayList<>();
      for (var event : meetings) {
        if (openMeeting(event) && date.equals(event.getStartsAt().atZone(ZONE).toLocalDate())) {
          Map<String, Object> meeting = new LinkedHashMap<>();
          meeting.put("id", event.getId());
          meeting.put("title", event.getTitle());
          meeting.put("startsAt", event.getStartsAt());
          meeting.put("endsAt", event.getEndsAt());
          meeting.put("relatedType", event.getRelatedType());
          meeting.put("relatedId", event.getRelatedId());
          dayMeetings.add(meeting);
        }
      }
      boolean inRange = !date.isBefore(rangeStart) && !date.isAfter(rangeEnd);
      Map<String, Object> day = new LinkedHashMap<>();
      day.put("date", date.toString());
      day.put("inRange", inRange);
      day.put("tasks", dayTasks);
      day.put("meetings", dayMeetings);
      days.add(day);
    }

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("timezone", ZONE.getId());
    out.put("view", mode);
    out.put("rangeStart", rangeStart.toString());
    out.put("rangeEnd", rangeEnd.toString());
    out.put("days", days);
    return out;
  }

  static boolean openMeeting(CrmCalendarEventEntity event) {
    return event.getStatus() == null || !"CANCELLED".equalsIgnoreCase(event.getStatus());
  }

  private static Map<String, Object> toTask(CrmTaskEntity t) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", t.getId());
    m.put("relatedType", t.getRelatedType());
    m.put("relatedId", t.getRelatedId());
    m.put("title", t.getTitle());
    m.put("status", t.getStatus());
    m.put("priority", t.getPriority());
    m.put("dueAt", t.getDueAt());
    m.put("ownerUserId", t.getOwnerUserId());
    m.put("sourceCode", t.getSourceCode());
    return m;
  }

  private static Map<String, Object> toHotLead(CrmLeadEntity l) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", l.getId());
    m.put("title", l.getTitle());
    m.put("displayName", l.getDisplayName());
    m.put("companyName", l.getCompanyName());
    m.put("score", l.getScore());
    m.put("status", l.getStatus());
    m.put("ownerUserId", l.getOwnerUserId());
    m.put("phone", l.getPhone());
    m.put("email", l.getEmail());
    return m;
  }
}
