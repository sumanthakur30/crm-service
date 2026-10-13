package com.shopmanagement.crmservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shopmanagement.crmservice.filter.TenantContextFilter;
import com.shopmanagement.crmservice.persistence.entity.CrmCalendarEventEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmTaskEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmCalendarEventRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmLeadRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmTaskRepository;

@ExtendWith(MockitoExtension.class)
class MyDayServiceWeekTest {

  @Mock private CrmTaskRepository taskRepository;
  @Mock private CrmLeadRepository leadRepository;
  @Mock private CrmCalendarEventRepository calendarRepository;
  @Mock private OpsService opsService;
  @Mock private ActivityService activityService;
  @Mock private ScoreBandService scoreBandService;
  @InjectMocks private MyDayService myDayService;

  @BeforeEach
  void bindTenant() {
    TenantContextFilter.bindTenantForTests("tenant-demo");
  }

  @AfterEach
  void clearTenant() {
    TenantContextFilter.clearTenantForTests();
  }

  @Test
  void weekPlacesAFollowUpOnItsKolkataDay() {
    CrmTaskEntity task = new CrmTaskEntity();
    task.setId(4L);
    task.setTitle("Follow up: Acme inquiry");
    task.setRelatedType("LEAD");
    task.setRelatedId(7L);
    task.setStatus("OPEN");
    task.setDueAt(FollowUpTaskService.dueAtEndOfDay(LocalDate.parse("2026-10-20")));

    CrmCalendarEventEntity meeting = new CrmCalendarEventEntity();
    meeting.setId(9L);
    meeting.setTitle("Demo");
    meeting.setRelatedType("LEAD");
    meeting.setRelatedId(7L);
    meeting.setStartsAt(Instant.parse("2026-10-21T04:30:00Z"));

    when(taskRepository
            .findByTenantIdAndStatusAndDueAtGreaterThanEqualAndDueAtLessThanAndDeletedAtIsNullOrderByDueAtAsc(
                eq("tenant-demo"), eq("OPEN"), any(), any()))
        .thenReturn(List.of(task));
    when(calendarRepository
            .findByTenantIdAndStartsAtGreaterThanEqualAndStartsAtLessThanAndDeletedAtIsNullOrderByStartsAtAsc(
                eq("tenant-demo"), any(), any()))
        .thenReturn(List.of(meeting));

    Map<String, Object> week = myDayService.week(LocalDate.parse("2026-10-21"));

    assertThat(week.get("weekStart")).isEqualTo("2026-10-19");
    assertThat(week.get("weekEnd")).isEqualTo("2026-10-25");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> days = (List<Map<String, Object>>) week.get("days");
    assertThat(days).hasSize(7);
    assertThat(tasksOn(days, "2026-10-20")).containsExactly("Follow up: Acme inquiry");
    assertThat(meetingsOn(days, "2026-10-21")).containsExactly("Demo");
    assertThat(tasksOn(days, "2026-10-19")).isEmpty();
  }

  @SuppressWarnings("unchecked")
  private static List<String> tasksOn(List<Map<String, Object>> days, String date) {
    return days.stream()
        .filter(day -> date.equals(day.get("date")))
        .flatMap(day -> ((List<Map<String, Object>>) day.get("tasks")).stream())
        .map(task -> String.valueOf(task.get("title")))
        .toList();
  }

  @SuppressWarnings("unchecked")
  private static List<String> meetingsOn(List<Map<String, Object>> days, String date) {
    return days.stream()
        .filter(day -> date.equals(day.get("date")))
        .flatMap(day -> ((List<Map<String, Object>>) day.get("meetings")).stream())
        .map(meeting -> String.valueOf(meeting.get("title")))
        .toList();
  }
}
