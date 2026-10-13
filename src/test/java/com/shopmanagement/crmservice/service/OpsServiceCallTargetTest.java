package com.shopmanagement.crmservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OpsServiceCallTargetTest {

  @Test
  void putsADealCallOnTheDeal() {
    assertThat(OpsService.callActivityType(null, 8L)).isEqualTo("OPPORTUNITY");
    assertThat(OpsService.callActivityId(null, 8L)).isEqualTo(8L);
  }

  @Test
  void keepsALeadCallOnTheLead() {
    assertThat(OpsService.callActivityType(4L, null)).isEqualTo("LEAD");
    assertThat(OpsService.callActivityId(4L, null)).isEqualTo(4L);
  }
}
