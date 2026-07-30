package com.shopmanagement.crmservice.api;

import java.time.Instant;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class CrmCampaignApi {

  private CrmCampaignApi() {}

  public record CampaignUpsert(
      @NotBlank @Size(max = 64) String code,
      @NotBlank @Size(max = 128) String name,
      @Size(max = 16) String status,
      @Size(max = 32) String channel,
      @Size(max = 128) String utmSource,
      @Size(max = 128) String utmMedium,
      @Size(max = 128) String utmCampaign,
      @Size(max = 128) String utmContent,
      @Size(max = 128) String utmTerm,
      @Size(max = 512) String landingUrl,
      Instant startsAt,
      Instant endsAt,
      Map<String, Object> attributes) {}

  public record CampaignResponse(
      Long id,
      String code,
      String name,
      String status,
      String channel,
      String utmSource,
      String utmMedium,
      String utmCampaign,
      String utmContent,
      String utmTerm,
      String landingUrl,
      String publicKey,
      String capturePath,
      Instant startsAt,
      Instant endsAt,
      Map<String, Object> attributes,
      Instant createdAt) {}

  public record PublicCaptureRequest(
      @NotBlank @Size(max = 256) String title,
      @Size(max = 256) String displayName,
      @Size(max = 256) String companyName,
      @Size(max = 256) String email,
      @Size(max = 64) String phone,
      @Size(max = 128) String utmSource,
      @Size(max = 128) String utmMedium,
      @Size(max = 128) String utmCampaign,
      @Size(max = 128) String utmContent,
      @Size(max = 128) String utmTerm,
      Map<String, Object> attributes) {}
}
