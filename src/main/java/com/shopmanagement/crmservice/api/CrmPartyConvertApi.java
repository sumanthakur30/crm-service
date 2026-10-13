package com.shopmanagement.crmservice.api;

import java.math.BigDecimal;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Sprint 2 — CRM-internal lead → account/contact/opportunity convert (not ERP handoff). */
public final class CrmPartyConvertApi {

  private CrmPartyConvertApi() {}

  public record AccountInput(
      @Size(max = 256) String name,
      @Size(max = 32) String gstin,
      @Size(max = 64) String phone,
      @Size(max = 256) String email,
      @Size(max = 8) String stateCode,
      @Size(max = 16) String pincode) {}

  public record ContactInput(
      @Size(max = 256) String displayName,
      @Size(max = 256) String email,
      @Size(max = 64) String phone,
      @Size(max = 128) String title) {}

  public record OpportunityInput(
      @Size(max = 256) String name,
      BigDecimal amount,
      @Size(max = 8) String currency,
      Long pipelineId,
      Long stageId) {}

  public record ConvertToCrmRequest(
      @NotBlank @Size(max = 16) String accountMode, // CREATE | EXISTING
      Long accountId,
      AccountInput account,
      @NotBlank @Size(max = 16) String contactMode, // CREATE | EXISTING | NONE
      Long contactId,
      ContactInput contact,
      @NotNull Boolean createOpportunity,
      OpportunityInput opportunity,
      Boolean markLeadConverted) {}

  public record ConvertToCrmResponse(
      Long leadId,
      Long accountId,
      Long contactId,
      Long opportunityId,
      String status,
      Map<String, Object> detail) {}
}
