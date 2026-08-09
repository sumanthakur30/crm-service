package com.shopmanagement.crmservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;

import org.junit.jupiter.api.Test;

class ErpFederationServiceTest {

  @Test
  void shopCustomerFromRefsReadsExternalId() {
    assertEquals(
        99L,
        ErpFederationService.shopCustomerFromRefs(
            Map.of("SHOP_CUSTOMER", Map.of("externalId", "99", "status", "SENT"))));
    assertNull(ErpFederationService.shopCustomerFromRefs(Map.of()));
    assertNull(ErpFederationService.shopCustomerFromRefs(null));
  }
}
