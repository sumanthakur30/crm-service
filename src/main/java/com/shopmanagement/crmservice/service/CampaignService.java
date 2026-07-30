package com.shopmanagement.crmservice.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.shopmanagement.crmservice.api.CrmCampaignApi.CampaignResponse;
import com.shopmanagement.crmservice.api.CrmCampaignApi.CampaignUpsert;
import com.shopmanagement.crmservice.api.CrmCampaignApi.PublicCaptureRequest;
import com.shopmanagement.crmservice.api.CrmLeadApi.LeadResponse;
import com.shopmanagement.crmservice.api.CrmLeadApi.LeadUpsert;
import com.shopmanagement.crmservice.filter.TenantContextFilter;
import com.shopmanagement.crmservice.persistence.entity.CrmCampaignEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmCampaignRepository;
import com.shopmanagement.crmservice.support.TenantIds;

@Service
public class CampaignService {

  private static final Set<String> STATUSES = Set.of("DRAFT", "ACTIVE", "PAUSED", "ENDED");

  private final CrmCampaignRepository campaignRepository;
  private final CrmLeadService leadService;

  public CampaignService(CrmCampaignRepository campaignRepository, CrmLeadService leadService) {
    this.campaignRepository = campaignRepository;
    this.leadService = leadService;
  }

  @Transactional
  public CampaignResponse upsert(CampaignUpsert body) {
    String tenantId = TenantIds.require();
    String code = body.code().trim().toUpperCase(Locale.ROOT);
    CrmCampaignEntity campaign =
        campaignRepository
            .findByTenantIdAndCodeAndDeletedAtIsNull(tenantId, code)
            .orElseGet(CrmCampaignEntity::new);
    if (campaign.getId() == null) {
      campaign.setTenantId(tenantId);
      campaign.setCode(code);
      campaign.setPublicKey(UUID.randomUUID().toString().replace("-", ""));
    }
    campaign.setName(body.name().trim());
    String status =
        body.status() == null || body.status().isBlank()
            ? (campaign.getStatus() == null ? "ACTIVE" : campaign.getStatus())
            : body.status().trim().toUpperCase(Locale.ROOT);
    if (!STATUSES.contains(status)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid campaign status");
    }
    campaign.setStatus(status);
    campaign.setChannel(blankToNull(body.channel()));
    campaign.setUtmSource(blankToNull(body.utmSource()));
    campaign.setUtmMedium(blankToNull(body.utmMedium()));
    campaign.setUtmCampaign(
        blankToNull(body.utmCampaign()) != null ? blankToNull(body.utmCampaign()) : code.toLowerCase(Locale.ROOT));
    campaign.setUtmContent(blankToNull(body.utmContent()));
    campaign.setUtmTerm(blankToNull(body.utmTerm()));
    campaign.setLandingUrl(blankToNull(body.landingUrl()));
    campaign.setStartsAt(body.startsAt());
    campaign.setEndsAt(body.endsAt());
    if (body.attributes() != null) {
      campaign.setAttributes(new LinkedHashMap<>(body.attributes()));
    } else if (campaign.getAttributes() == null) {
      campaign.setAttributes(new LinkedHashMap<>());
    }
    campaign.touch();
    return toResponse(campaignRepository.save(campaign));
  }

  @Transactional(readOnly = true)
  public List<CampaignResponse> list() {
    return campaignRepository
        .findByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc(TenantIds.require())
        .stream()
        .map(CampaignService::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public CampaignResponse get(Long id) {
    return toResponse(require(TenantIds.require(), id));
  }

  @Transactional
  public LeadResponse capturePublic(String publicKey, PublicCaptureRequest body) {
    CrmCampaignEntity campaign =
        campaignRepository
            .findByPublicKeyAndDeletedAtIsNull(publicKey)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Campaign not found"));
    if (!"ACTIVE".equalsIgnoreCase(campaign.getStatus())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Campaign is not ACTIVE");
    }
    Instant now = Instant.now();
    if (campaign.getStartsAt() != null && now.isBefore(campaign.getStartsAt())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Campaign has not started");
    }
    if (campaign.getEndsAt() != null && now.isAfter(campaign.getEndsAt())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Campaign has ended");
    }

    TenantContextFilter.bindTenant(campaign.getTenantId());
    String source =
        firstNonBlank(body.utmSource(), campaign.getUtmSource(), campaign.getChannel(), "CAMPAIGN");
    return leadService.create(
        new LeadUpsert(
            body.title(),
            body.displayName(),
            body.companyName(),
            body.email(),
            body.phone(),
            source,
            "OPEN",
            "MEDIUM",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            body.attributes(),
            null,
            "public-capture",
            campaign.getId(),
            firstNonBlank(body.utmSource(), campaign.getUtmSource()),
            firstNonBlank(body.utmMedium(), campaign.getUtmMedium()),
            firstNonBlank(body.utmCampaign(), campaign.getUtmCampaign()),
            firstNonBlank(body.utmContent(), campaign.getUtmContent()),
            firstNonBlank(body.utmTerm(), campaign.getUtmTerm())));
  }

  private CrmCampaignEntity require(String tenantId, Long id) {
    return campaignRepository
        .findByTenantIdAndIdAndDeletedAtIsNull(tenantId, id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Campaign not found"));
  }

  private static CampaignResponse toResponse(CrmCampaignEntity c) {
    return new CampaignResponse(
        c.getId(),
        c.getCode(),
        c.getName(),
        c.getStatus(),
        c.getChannel(),
        c.getUtmSource(),
        c.getUtmMedium(),
        c.getUtmCampaign(),
        c.getUtmContent(),
        c.getUtmTerm(),
        c.getLandingUrl(),
        c.getPublicKey(),
        "/api/v1/crm/public/capture/" + c.getPublicKey(),
        c.getStartsAt(),
        c.getEndsAt(),
        c.getAttributes(),
        c.getCreatedAt());
  }

  private static String blankToNull(String v) {
    return v == null || v.isBlank() ? null : v.trim();
  }

  private static String firstNonBlank(String... values) {
    if (values == null) {
      return null;
    }
    for (String v : values) {
      if (v != null && !v.isBlank()) {
        return v.trim();
      }
    }
    return null;
  }
}
