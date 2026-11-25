package com.shopmanagement.crmservice.integration.meta;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.shopmanagement.crmservice.config.CrmMetaProperties;
import com.shopmanagement.crmservice.inbound.InboundHmacVerifier;
import com.shopmanagement.crmservice.persistence.entity.CrmInboundEventEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmIntegrationAssetEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmJobEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmInboundEventRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmIntegrationAssetRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmIntegrationRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmJobRepository;

/** Official leadgen webhook. An unknown page is stored and does not create a lead. */
@Service
public class MetaWebhookService {

  private final CrmMetaProperties properties;
  private final CrmInboundEventRepository inboundRepository;
  private final CrmIntegrationAssetRepository assetRepository;
  private final CrmIntegrationRepository integrationRepository;
  private final CrmJobRepository jobRepository;

  public MetaWebhookService(
      CrmMetaProperties properties,
      CrmInboundEventRepository inboundRepository,
      CrmIntegrationAssetRepository assetRepository,
      CrmIntegrationRepository integrationRepository,
      CrmJobRepository jobRepository) {
    this.properties = properties;
    this.inboundRepository = inboundRepository;
    this.assetRepository = assetRepository;
    this.integrationRepository = integrationRepository;
    this.jobRepository = jobRepository;
  }

  public String challenge(String mode, String verifyToken, String challenge) {
    if (!"subscribe".equals(mode) || challenge == null || challenge.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Facebook verification is incomplete");
    }
    String expected = properties.getVerifyToken();
    if (expected == null || expected.isBlank() || !expected.equals(verifyToken)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Facebook verify token does not match");
    }
    return challenge;
  }

  @Transactional
  public Map<String, Object> receive(byte[] rawBody, String signature, Map<String, Object> payload) {
    String secret = properties.getAppSecret();
    if (secret == null || secret.isBlank() || !InboundHmacVerifier.matches(rawBody, secret, signature)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Facebook signature does not match");
    }
    int accepted = 0;
    for (LeadNotice notice : notices(payload)) {
      accepted++;
      acceptOne(notice, payload);
    }
    return Map.of("accepted", accepted);
  }

  private void acceptOne(LeadNotice notice, Map<String, Object> payload) {
    if (inboundRepository.findByProviderAndExternalId("META", notice.leadgenId()).isPresent()) {
      return;
    }
    CrmIntegrationAssetEntity page =
        assetRepository
            .findByAssetTypeAndExternalIdAndSelectedTrue("PAGE", notice.pageId())
            .orElse(null);
    CrmInboundEventEntity event = new CrmInboundEventEntity();
    event.setProvider("META");
    event.setExternalId(notice.leadgenId());
    event.setPayloadJson(new LinkedHashMap<>(payload == null ? Map.of() : payload));
    event.setAttemptCount(0);
    event.setSignatureOk(true);
    if (page == null) {
      event.setStatus("FAILED");
      event.setErrorMessage("page not selected");
      event.setProcessedAt(Instant.now());
      inboundRepository.save(event);
      alertUnselected(notice.pageId());
      return;
    }
    event.setTenantId(page.getTenantId());
    event.setBranchId(page.getBranchId());
    event.setStatus("RECEIVED");
    event = inboundRepository.save(event);
    CrmJobEntity job = new CrmJobEntity();
    job.setTenantId(page.getTenantId());
    job.setJobType(MetaLeadFetchService.JOB_TYPE);
    job.setRefId(event.getId());
    job.setStatus("PENDING");
    job.setRunAt(Instant.now());
    job.setAttempts(0);
    jobRepository.save(job);
  }

  private void alertUnselected(String pageId) {
    for (CrmIntegrationAssetEntity asset : assetRepository.findByAssetTypeAndExternalId("PAGE", pageId)) {
      integrationRepository
          .findById(asset.getIntegrationId())
          .ifPresent(
              integration -> {
                integration.setLastError("page not selected");
                integration.touch();
                integrationRepository.save(integration);
              });
    }
  }

  @SuppressWarnings("unchecked")
  static List<LeadNotice> notices(Map<String, Object> payload) {
    List<LeadNotice> notices = new ArrayList<>();
    if (payload == null || !(payload.get("entry") instanceof List<?> entries)) {
      return notices;
    }
    for (Object entry : entries) {
      if (!(entry instanceof Map<?, ?> entryMap) || !(entryMap.get("changes") instanceof List<?> changes)) {
        continue;
      }
      for (Object change : changes) {
        if (!(change instanceof Map<?, ?> changeMap)) {
          continue;
        }
        if (!"leadgen".equals(String.valueOf(changeMap.get("field")))) {
          continue;
        }
        if (!(changeMap.get("value") instanceof Map<?, ?> value)) {
          continue;
        }
        String leadgenId = text(value.get("leadgen_id"));
        String pageId = text(value.get("page_id"));
        if (leadgenId != null && pageId != null) {
          notices.add(new LeadNotice(leadgenId, pageId));
        }
      }
    }
    return notices;
  }

  private static String text(Object value) {
    if (value == null) {
      return null;
    }
    String text = String.valueOf(value).trim();
    return text.isEmpty() ? null : text;
  }

  record LeadNotice(String leadgenId, String pageId) {}
}
