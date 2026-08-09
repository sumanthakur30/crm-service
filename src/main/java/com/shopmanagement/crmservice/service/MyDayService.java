package com.shopmanagement.crmservice.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
