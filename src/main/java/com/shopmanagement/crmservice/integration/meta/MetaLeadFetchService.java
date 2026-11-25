package com.shopmanagement.crmservice.integration.meta;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopmanagement.crmservice.api.CrmLeadApi.LeadUpsert;
import com.shopmanagement.crmservice.filter.TenantContextFilter;
import com.shopmanagement.crmservice.integration.IntegrationCipher;
import com.shopmanagement.crmservice.integration.meta.MetaGraphClient.LeadDetails;
import com.shopmanagement.crmservice.integration.meta.MetaLeadMapper.Mapped;
import com.shopmanagement.crmservice.persistence.entity.CrmInboundEventEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmIntegrationAssetEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmIntegrationEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmIntegrationSecretEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmJobEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmFieldMapRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmInboundEventRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmIntegrationAssetRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmIntegrationRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmIntegrationSecretRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmJobRepository;
import com.shopmanagement.crmservice.service.CrmLeadService;

/** Fetches the leadgen id and creates one enquiry on the selected page's branch. */
@Service
public class MetaLeadFetchService {

  static final String JOB_TYPE = "META_LEAD_FETCH";
  private static final int MAX_ATTEMPTS = 5;

  private final CrmJobRepository jobRepository;
  private final CrmInboundEventRepository inboundRepository;
  private final CrmIntegrationRepository integrationRepository;
  private final CrmIntegrationAssetRepository assetRepository;
  private final CrmIntegrationSecretRepository secretRepository;
  private final CrmFieldMapRepository fieldMapRepository;
  private final IntegrationCipher cipher;
  private final MetaGraphClient graph;
  private final CrmLeadService leadService;

  public MetaLeadFetchService(
      CrmJobRepository jobRepository,
      CrmInboundEventRepository inboundRepository,
      CrmIntegrationRepository integrationRepository,
      CrmIntegrationAssetRepository assetRepository,
      CrmIntegrationSecretRepository secretRepository,
      CrmFieldMapRepository fieldMapRepository,
      IntegrationCipher cipher,
      MetaGraphClient graph,
      CrmLeadService leadService) {
    this.jobRepository = jobRepository;
    this.inboundRepository = inboundRepository;
    this.integrationRepository = integrationRepository;
    this.assetRepository = assetRepository;
    this.secretRepository = secretRepository;
    this.fieldMapRepository = fieldMapRepository;
    this.cipher = cipher;
    this.graph = graph;
    this.leadService = leadService;
  }

  @Transactional
  public int processDue() {
    List<CrmJobEntity> due =
        jobRepository.findByStatusAndRunAtLessThanEqual("PENDING", Instant.now(), PageRequest.of(0, 20));
    int ran = 0;
    for (CrmJobEntity job : due) {
      if (!JOB_TYPE.equals(job.getJobType())) {
        continue;
      }
      run(job);
      ran++;
    }
    return ran;
  }

  private void run(CrmJobEntity job) {
    CrmInboundEventEntity event = inboundRepository.findById(job.getRefId()).orElse(null);
    if (event == null || event.getLeadId() != null) {
      finish(job, "DONE", null);
      return;
    }
    CrmIntegrationEntity integration =
        integrationRepository.findByTenantIdAndProviderAndDeletedAtIsNull(job.getTenantId(), "META").orElse(null);
    if (integration != null && "ERROR".equals(integration.getStatus())) {
      job.setRunAt(Instant.now().plus(Duration.ofHours(1)));
      job.setLastError("Reconnect Facebook. The page token is no longer valid.");
      jobRepository.save(job);
      return;
    }
    String pageId = pageId(event);
    CrmIntegrationAssetEntity page =
        pageId == null
            ? null
            : assetRepository.findByAssetTypeAndExternalIdAndSelectedTrue("PAGE", pageId).orElse(null);
    if (page == null) {
      failEvent(event, "page not selected");
      finish(job, "FAILED", "page not selected");
      return;
    }
    CrmIntegrationSecretEntity secret =
        secretRepository.findByAssetIdAndSecretKind(page.getId(), "PAGE_TOKEN").orElse(null);
    if (secret == null) {
      failEvent(event, "page token missing");
      finish(job, "FAILED", "page token missing");
      return;
    }
    boolean compact = job.getAttempts() > 0;
    try {
      LeadDetails lead = graph.fetchLead(event.getExternalId(), cipher.decrypt(secret.getCiphertext()), compact);
      if (!createLead(event, page, lead)) {
        finish(job, "FAILED", event.getErrorMessage());
        return;
      }
      finish(job, "DONE", null);
      if (integration != null) {
        integration.setLastSuccessAt(Instant.now());
        integration.setLastError(null);
        if (!"ERROR".equals(integration.getStatus())) {
          integration.setStatus("CONNECTED");
        }
        integration.touch();
        integrationRepository.save(integration);
      }
    } catch (MetaGraphException ex) {
      job.setAttempts(job.getAttempts() + 1);
      job.setLastError(clip(ex.getMessage()));
      if (ex.isPermissionFailure() || job.getAttempts() >= MAX_ATTEMPTS) {
        if (ex.isPermissionFailure() && integration != null) {
          integration.setStatus("ERROR");
          integration.setLastError("Reconnect Facebook. The page token is no longer valid.");
          integration.touch();
          integrationRepository.save(integration);
        }
        failEvent(event, clip(ex.getMessage()));
        finish(job, "FAILED", clip(ex.getMessage()));
        return;
      }
      job.setRunAt(Instant.now().plus(backoff(job.getAttempts())));
      jobRepository.save(job);
    }
  }

  private boolean createLead(CrmInboundEventEntity event, CrmIntegrationAssetEntity page, LeadDetails details) {
    CrmIntegrationAssetEntity form = null;
    if (details.formId() != null) {
      form =
          assetRepository
              .findByIntegrationIdAndAssetTypeAndExternalId(page.getIntegrationId(), "FORM", details.formId())
              .orElse(null);
    }
    Long branchId = page.getBranchId();
    if (form != null && form.isSelected() && form.getBranchId() != null) {
      branchId = form.getBranchId();
    }
    Mapped mapped =
        MetaLeadMapper.map(
            details.fieldData(),
            form == null ? List.of() : fieldMapRepository.findByAssetIdOrderByIdAsc(form.getId()),
            null);
    if (mapped.phone() == null && mapped.email() == null) {
      failEvent(event, "Facebook lead has no mobile or email");
      return false;
    }
    Map<String, Object> attributes = new LinkedHashMap<>(mapped.extras());
    if (mapped.course() != null) {
      attributes.put("course", mapped.course());
    }
    if (branchId != null) {
      attributes.put("branchId", branchId);
    }
    Map<String, Object> refs = new LinkedHashMap<>();
    refs.put("externalId", event.getExternalId());
    refs.put("rawEventId", event.getId());
    put(refs, "pageExternalId", page.getExternalId());
    put(refs, "formExternalId", details.formId());
    put(refs, "campaignExternalId", details.campaignId());
    put(refs, "campaignName", details.campaignName());
    put(refs, "adExternalId", details.adId());
    put(refs, "adName", details.adName());
    put(refs, "adsetExternalId", details.adsetId());
    put(refs, "adsetName", details.adsetName());
    put(refs, "instagramActorId", details.instagramActorId());
    TenantContextFilter.bindTenant(page.getTenantId());
    var response =
        leadService.create(
            new LeadUpsert(
                mapped.title(),
                mapped.name(),
                null,
                mapped.email(),
                mapped.phone(),
                "FACEBOOK_LEAD_AD",
                "OPEN",
                "MEDIUM",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                attributes,
                refs,
                details.formId(),
                page.getCampaignId(),
                "facebook",
                "lead_ad",
                details.campaignName(),
                details.adName(),
                null,
                null,
                null),
            true);
    event.setLeadId(response.id());
    event.setStatus("MAPPED");
    event.setBranchId(branchId);
    event.setProcessedAt(Instant.now());
    event.setErrorMessage(null);
    inboundRepository.save(event);
    return true;
  }

  private void failEvent(CrmInboundEventEntity event, String message) {
    event.setStatus("FAILED");
    event.setErrorMessage(clip(message));
    event.setProcessedAt(Instant.now());
    inboundRepository.save(event);
  }

  private void finish(CrmJobEntity job, String status, String error) {
    job.setStatus(status);
    job.setLastError(error);
    job.setLockedAt(null);
    if ("DONE".equals(status)) {
      job.setAttempts(job.getAttempts() + 1);
    }
    jobRepository.save(job);
  }

  private static String pageId(CrmInboundEventEntity event) {
    for (MetaWebhookService.LeadNotice notice : MetaWebhookService.notices(event.getPayloadJson())) {
      if (notice.leadgenId().equals(event.getExternalId())) {
        return notice.pageId();
      }
    }
    return null;
  }

  private static void put(Map<String, Object> refs, String key, String value) {
    if (value != null && !value.isBlank()) {
      refs.put(key, value);
    }
  }

  private static Duration backoff(int attempt) {
    return switch (attempt) {
      case 1 -> Duration.ofMinutes(1);
      case 2 -> Duration.ofMinutes(5);
      case 3 -> Duration.ofMinutes(15);
      default -> Duration.ofHours(1);
    };
  }

  private static String clip(String message) {
    if (message == null || message.isBlank()) {
      return "Facebook request failed";
    }
    return message.length() <= 300 ? message : message.substring(0, 300);
  }
}
