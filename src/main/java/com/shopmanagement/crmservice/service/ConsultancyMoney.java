package com.shopmanagement.crmservice.service;

import java.math.BigDecimal;
import java.util.List;

/** Separates student fees, institution commission, consultant payables, and expenses. */
public final class ConsultancyMoney {

  public record Line(String ledger, String kind, BigDecimal amount) {}

  public record Summary(
      BigDecimal feePayable,
      BigDecimal feeReceived,
      BigDecimal feeOutstanding,
      BigDecimal commissionExpected,
      BigDecimal commissionReceived,
      BigDecimal commissionOutstanding,
      BigDecimal consultantEntitled,
      BigDecimal consultantPaid,
      BigDecimal consultantOutstanding,
      BigDecimal expenses,
      BigDecimal realizedContribution) {}

  private ConsultancyMoney() {}

  public static Summary summarize(List<Line> lines) {
    BigDecimal feeSchedule = sum(lines, "STUDENT_FEE", "SCHEDULE");
    BigDecimal feeAdjust = sum(lines, "STUDENT_FEE", "ADJUSTMENT");
    BigDecimal feeReceipt = sum(lines, "STUDENT_FEE", "RECEIPT");
    BigDecimal feeRefund = sum(lines, "STUDENT_FEE", "REFUND");
    BigDecimal payable = feeSchedule.add(feeAdjust);
    BigDecimal receivedFees = feeReceipt.subtract(feeRefund);
    BigDecimal expected = sum(lines, "INSTITUTION_COMMISSION", "EXPECTED");
    BigDecimal commissionIn = sum(lines, "INSTITUTION_COMMISSION", "RECEIPT");
    BigDecimal entitled = sum(lines, "CONSULTANT_PAYABLE", "EXPECTED");
    BigDecimal paid = sum(lines, "CONSULTANT_PAYABLE", "PAYMENT");
    BigDecimal expenses = sum(lines, "EXPENSE", "PAYMENT").add(sum(lines, "EXPENSE", "EXPECTED"));
    return new Summary(
        payable,
        receivedFees,
        payable.subtract(receivedFees),
        expected,
        commissionIn,
        expected.subtract(commissionIn),
        entitled,
        paid,
        entitled.subtract(paid),
        expenses,
        commissionIn.subtract(paid).subtract(expenses));
  }

  private static BigDecimal sum(List<Line> lines, String ledger, String kind) {
    BigDecimal total = BigDecimal.ZERO;
    if (lines == null) {
      return total;
    }
    for (Line line : lines) {
      if (line == null || line.amount() == null) {
        continue;
      }
      if (ledger.equals(line.ledger()) && kind.equals(line.kind())) {
        total = total.add(line.amount());
      }
    }
    return total;
  }
}
