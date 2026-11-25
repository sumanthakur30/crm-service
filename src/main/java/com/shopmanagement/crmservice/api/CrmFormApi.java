package com.shopmanagement.crmservice.api;

import java.time.Instant;
import java.util.List;

public final class CrmFormApi {

  private CrmFormApi() {}

  public record FieldMapInput(String externalKey, String target, Boolean required) {}

  public record FieldMapResponse(Long id, String externalKey, String target, boolean required) {}

  public record WebFormUpsert(
      String name,
      Long branchId,
      Long campaignId,
      String defaultCourse,
      String honeypotField,
      List<FieldMapInput> fields) {}

  public record WebFormResponse(
      Long id,
      String name,
      String publicKey,
      String status,
      Long branchId,
      Long campaignId,
      String defaultCourse,
      String honeypotField,
      String capturePath,
      List<FieldMapResponse> fields,
      Instant createdAt) {}
}
