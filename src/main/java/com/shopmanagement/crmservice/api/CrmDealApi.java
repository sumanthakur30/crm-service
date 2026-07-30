package com.shopmanagement.crmservice.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class CrmDealApi {

  private CrmDealApi() {}

  public record OpportunityUpsert(
      @NotBlank @Size(max = 256) String name,
      Long leadId,
      Long accountId,
      Long pipelineId,
      Long stageId,
      BigDecimal amount,
      @Size(max = 8) String currency,
      @Min(0) @Max(100) Integer probability,
      LocalDate expectedCloseDate,
      @Size(max = 32) String status,
      @Size(max = 64) String ownerUserId,
      @Size(max = 64) String teamId,
      Map<String, Object> attributes,
      @Size(max = 64) String closeReasonCode,
      @Size(max = 512) String closeReasonNote) {}

  public record OpportunityResponse(
      Long id,
      String tenantId,
      Long pipelineId,
      Long stageId,
      Long leadId,
      Long accountId,
      String name,
      BigDecimal amount,
      String currency,
      int probability,
      LocalDate expectedCloseDate,
      String status,
      String ownerUserId,
      String teamId,
      Map<String, Object> attributes,
      String closeReasonCode,
      String closeReasonNote,
      Instant createdAt,
      Instant updatedAt) {}

  public record OpportunityStageMove(
      @Size(max = 64) String closeReasonCode, @Size(max = 512) String closeReasonNote) {}

  public record QuoteLine(
      @NotBlank String description,
      String hsn,
      @NotNull BigDecimal qty,
      @NotNull BigDecimal unitPrice,
      @NotNull BigDecimal gstRate,
      BigDecimal discount) {}

  public record QuotationUpsert(
      @NotNull Long opportunityId,
      String customerName,
      String customerGstin,
      String placeOfSupply,
      String sellerStateCode,
      String buyerStateCode,
      String currency,
      BigDecimal discountAmount,
      String terms,
      LocalDate validUntil,
      List<QuoteLine> lines) {}

  public record QuotationResponse(
      Long id,
      Long opportunityId,
      String quoteNumber,
      int versionNo,
      Long parentQuotationId,
      String status,
      String approvalStatus,
      Long approvalId,
      String customerName,
      String customerGstin,
      String placeOfSupply,
      String sellerStateCode,
      String buyerStateCode,
      String currency,
      BigDecimal taxableAmount,
      BigDecimal cgstAmount,
      BigDecimal sgstAmount,
      BigDecimal igstAmount,
      BigDecimal totalAmount,
      BigDecimal discountAmount,
      String terms,
      List<Map<String, Object>> lines,
      Map<String, Object> sharePayload,
      LocalDate validUntil,
      Instant acceptedAt,
      Instant createdAt,
      String paymentLinkUrl,
      String paymentStatus,
      String paymentProvider,
      String paymentRef,
      BigDecimal paymentAmount,
      Instant paidAt) {}

  /** Optional body for POST /quotations/{id}/send — omit channel to mark SENT only. */
  public record QuotationSendRequest(
      @Size(max = 16) String channel,
      @Size(max = 256) String recipient) {}
}
