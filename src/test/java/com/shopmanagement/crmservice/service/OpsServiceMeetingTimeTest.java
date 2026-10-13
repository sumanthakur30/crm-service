package com.shopmanagement.crmservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.shopmanagement.crmservice.persistence.entity.CrmCalendarEventEntity;

class OpsServiceMeetingTimeTest {

  @Test
  void keepsTheKolkataMorningOnThatCalendarDay() {
    Instant start = OpsService.parseMeetingInstant("2026-10-21T04:30:00Z");
    assertThat(start).isEqualTo(Instant.parse("2026-10-21T04:30:00Z"));
    assertThat(start.atZone(MyDayService.ZONE).toLocalDate().toString()).isEqualTo("2026-10-21");
  }

  @Test
  void rejectsAMissingMeetingTime() {
    assertThatThrownBy(() -> OpsService.parseMeetingInstant("  "))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void keepsTheBookedLengthWhenTheStartMoves() {
    Instant start = Instant.parse("2026-10-21T04:30:00Z");
    Instant end = Instant.parse("2026-10-21T05:00:00Z");
    Instant moved = Instant.parse("2026-10-22T04:30:00Z");
    assertThat(OpsService.movedEnd(start, end, moved)).isEqualTo(Instant.parse("2026-10-22T05:00:00Z"));
  }

  @Test
  void usesThirtyMinutesWhenTheMeetingHasNoEnd() {
    Instant moved = Instant.parse("2026-10-21T06:30:00Z");
    assertThat(OpsService.movedEnd(Instant.parse("2026-10-21T04:30:00Z"), null, moved))
        .isEqualTo(Instant.parse("2026-10-21T07:00:00Z"));
  }

  @Test
  void hidesACancelledMeetingFromTheCalendar() {
    CrmCalendarEventEntity event = new CrmCalendarEventEntity();
    event.setStatus("CANCELLED");
    assertThat(MyDayService.openMeeting(event)).isFalse();
    event.setStatus("SCHEDULED");
    assertThat(MyDayService.openMeeting(event)).isTrue();
  }
}
