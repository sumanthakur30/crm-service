package com.shopmanagement.crmservice.support;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ImportColumnsTest {

  @Test
  void phoneAndNameColumnsAreSharedWithForms() {
    assertEquals("phone", ImportColumns.suggest("Mobile Number"));
    assertEquals("title", ImportColumns.suggest("name"));
    assertEquals("+919999999999", ImportColumns.phone("99999 99999"));
  }
}
