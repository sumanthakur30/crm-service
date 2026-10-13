package com.shopmanagement.crmservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

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
}
