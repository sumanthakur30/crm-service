package com.shopmanagement.crmservice.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class CrmSequenceApi {

  private CrmSequenceApi() {}

  public record SequenceStepUpsert(
      int sortOrder,
      int delayHours,
      @NotBlank @Size(max = 16) String channel,
      @Size(max = 256) String subjectTemplate,
      @NotBlank String bodyTemplate) {}

  public record SequenceUpsert(
      @NotBlank @Size(max = 64) String code,
      @NotBlank @Size(max = 128) String name,
      @Size(max = 16) String channelDefault,
      List<SequenceStepUpsert> steps) {}

  public record SequenceStepResponse(
      Long id, int sortOrder, int delayHours, String channel, String subjectTemplate, String bodyTemplate) {}

  public record SequenceResponse(
      Long id,
      String code,
      String name,
      String channelDefault,
      boolean active,
      List<SequenceStepResponse> steps) {}

  public record EnrollRequest(
      @NotNull Long sequenceId,
      Long leadId,
      Long opportunityId,
      @NotBlank @Size(max = 256) String recipient,
      @Size(max = 16) String channel) {}

  public record EnrollmentResponse(
      Long id,
      Long sequenceId,
      Long leadId,
      Long opportunityId,
      String recipient,
      String channel,
      String status,
      int currentStep,
      Instant nextRunAt,
      String lastError,
      Map<String, Object> attributes,
      Instant completedAt) {}

  public record ProcessDueResponse(int processed, int completed, int failed) {}
}
