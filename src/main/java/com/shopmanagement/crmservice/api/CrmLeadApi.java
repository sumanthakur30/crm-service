package com.shopmanagement.crmservice.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class CrmLeadApi {

  private CrmLeadApi() {}

  public record LeadUpsert(
      @NotBlank @Size(max = 256) String title,
      @Size(max = 256) String displayName,
      @Size(max = 256) String companyName,
      @Size(max = 256) String email,
      @Size(max = 64) String phone,
      @Size(max = 64) String sourceCode,
      @Size(max = 32) String status,
      @Size(max = 32) String priority,
      @Min(0) @Max(100) Integer score,
      @Size(max = 64) String ownerUserId,
      @Size(max = 64) String teamId,
      BigDecimal amount,
      @Size(max = 8) String currency,
      Long pipelineId,
      Long stageId,
      Map<String, Object> attributes,
      Map<String, Object> externalRefs,
      @Size(max = 128) String formKey,
      Long campaignId,
      @Size(max = 128) String utmSource,
      @Size(max = 128) String utmMedium,
      @Size(max = 128) String utmCampaign,
      @Size(max = 128) String utmContent,
      @Size(max = 128) String utmTerm,
      Long accountId,
      Long contactId) {}

  public record LeadStatusPatch(
      @NotBlank @Size(max = 32) String status, Long stageId, @Size(max = 64) String lostReasonCode) {}

  public record LeadResponse(
      Long id,
      String tenantId,
      Long pipelineId,
      Long stageId,
      String title,
      String displayName,
      String companyName,
      String email,
      String phone,
      String sourceCode,
      String status,
      String priority,
      int score,
      String ownerUserId,
      String teamId,
      BigDecimal amount,
      String currency,
      Map<String, Object> attributes,
      Map<String, Object> externalRefs,
      String formKey,
      Long campaignId,
      String utmSource,
      String utmMedium,
      String utmCampaign,
      String utmContent,
      String utmTerm,
      Long accountId,
      Long contactId,
      Instant createdAt,
      Instant updatedAt) {}

  public record WorkspaceBootstrapRequest(
      @Size(max = 128) String name, @Size(max = 64) String templateCode) {}

  public record WorkspaceResponse(
      Long id,
      String tenantId,
      String name,
      String templateCode,
      String timezone,
      String currency,
      Long defaultPipelineId) {}

  public record PipelineResponse(Long id, String code, String name, String objectType, boolean isDefault) {}

  public record StageResponse(
      Long id, Long pipelineId, String code, String name, int sortOrder, int probability, boolean won, boolean lost) {}

  public record StatusResponse(
      String service, String phase, boolean entitlementCheckEnabled, boolean convertEnabled) {}

  public record AssignRequest(String mode, String ownerUserId, String teamId) {}

  public record TeamMemberRequest(
      String teamId,
      String userId,
      String displayName,
      Boolean active,
      Integer sortOrder,
      String stateCodes,
      String pincodePrefixes,
      Integer openLeadCap) {}

  public record TeamMemberResponse(
      Long id,
      String teamId,
      String userId,
      String displayName,
      boolean active,
      int sortOrder,
      String stateCodes,
      String pincodePrefixes,
      int openLeadCap) {}

  public record ImportResult(int totalRows, int created, int skipped, List<String> errors) {}

  public record NoteRequest(@NotBlank String body) {}

  public record NoteResponse(
      Long id, String relatedType, Long relatedId, String body, String authorUserId, Instant createdAt) {}

  public record TimelineItem(
      String kind,
      Long id,
      String eventType,
      String summary,
      String actorUserId,
      Instant occurredAt,
      Map<String, Object> payload) {}
}
