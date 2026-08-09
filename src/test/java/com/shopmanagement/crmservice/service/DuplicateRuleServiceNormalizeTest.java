package com.shopmanagement.crmservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class DuplicateRuleServiceNormalizeTest {

  @Test
  void digitsOnlyStripsNonDigits() {
    assertEquals("919876543210", DuplicateRuleService.normalize("+91-98765 43210", "DIGITS_ONLY"));
    assertNull(DuplicateRuleService.normalize("abc", "DIGITS_ONLY"));
  }

  @Test
  void lowerTrimsAndLowercases() {
    assertEquals("a@b.co", DuplicateRuleService.normalize("  A@B.CO ", "LOWER"));
  }

  @Test
  void exactTrimsOnly() {
    assertEquals("Acme", DuplicateRuleService.normalize(" Acme ", "EXACT"));
    assertNull(DuplicateRuleService.normalize("  ", "EXACT"));
    assertNull(DuplicateRuleService.normalize(null, "LOWER"));
  }
}
