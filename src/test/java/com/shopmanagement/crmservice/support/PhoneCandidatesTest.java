package com.shopmanagement.crmservice.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PhoneCandidatesTest {

  @Test
  void tenDigitIndianNumberMatchesStoredForms() {
    assertThat(PhoneCandidates.of("+91 99999 99999"))
        .contains("9999999999", "919999999999", "+919999999999");
  }

  @Test
  void tenDigitNumberBecomesIndiaE164() {
    assertThat(PhoneCandidates.e164("99999 99999")).isEqualTo("+919999999999");
  }

  @Test
  void blankPhoneHasNoCandidates() {
    assertThat(PhoneCandidates.of("  ")).isEmpty();
  }
}
