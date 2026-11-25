package com.shopmanagement.crmservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.shopmanagement.crmservice.service.ConsultancyMoney.Line;
import com.shopmanagement.crmservice.service.ConsultancyMoney.Summary;

class ConsultancyMoneyTest {

  @Test
  void feeDepositIsNotConsultancyIncome() {
    Summary summary =
        ConsultancyMoney.summarize(
            List.of(
                new Line("STUDENT_FEE", "SCHEDULE", new BigDecimal("150000")),
                new Line("STUDENT_FEE", "RECEIPT", new BigDecimal("150000")),
                new Line("INSTITUTION_COMMISSION", "EXPECTED", new BigDecimal("25000")),
                new Line("INSTITUTION_COMMISSION", "RECEIPT", new BigDecimal("25000"))));

    assertThat(summary.feeOutstanding()).isEqualByComparingTo("0");
    assertThat(summary.realizedContribution()).isEqualByComparingTo("25000");
  }

  @Test
  void partialConsultantPaymentLeavesOutstanding() {
    Summary summary =
        ConsultancyMoney.summarize(
            List.of(
                new Line("INSTITUTION_COMMISSION", "RECEIPT", new BigDecimal("40000")),
                new Line("CONSULTANT_PAYABLE", "EXPECTED", new BigDecimal("12000")),
                new Line("CONSULTANT_PAYABLE", "PAYMENT", new BigDecimal("7000"))));

    assertThat(summary.consultantOutstanding()).isEqualByComparingTo("5000");
    assertThat(summary.realizedContribution()).isEqualByComparingTo("33000");
  }

  @Test
  void expectedCommissionIsNotCash() {
    Summary summary =
        ConsultancyMoney.summarize(
            List.of(
                new Line("INSTITUTION_COMMISSION", "EXPECTED", new BigDecimal("30000")),
                new Line("INSTITUTION_COMMISSION", "RECEIPT", new BigDecimal("10000")),
                new Line("CONSULTANT_PAYABLE", "EXPECTED", new BigDecimal("8000"))));

    assertThat(summary.commissionOutstanding()).isEqualByComparingTo("20000");
    assertThat(summary.realizedContribution()).isEqualByComparingTo("10000");
  }
}
