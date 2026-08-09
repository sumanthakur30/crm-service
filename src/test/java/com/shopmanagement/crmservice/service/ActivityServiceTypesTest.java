package com.shopmanagement.crmservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

class ActivityServiceTypesTest {

  @Test
  void parseTypesDefaultsAndFilters() {
    assertEquals(Set.of("TASK", "MEETING", "CALL", "NOTE"), ActivityService.parseTypes(null));
    assertEquals(Set.of("TASK", "CALL"), ActivityService.parseTypes("task, call"));
    assertTrue(ActivityService.parseTypes("").contains("MEETING"));
  }
}
