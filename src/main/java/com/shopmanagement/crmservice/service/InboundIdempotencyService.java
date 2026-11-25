package com.shopmanagement.crmservice.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.shopmanagement.crmservice.persistence.entity.CrmInboundEventEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmInboundEventRepository;

/** Raw inbound row. A key is committed before the lead so a replay can see it. */
@Service
public class InboundIdempotencyService {

  static final String PROVIDER = "WEB";

  private final CrmInboundEventRepository inboundRepository;

  public InboundIdempotencyService(CrmInboundEventRepository inboundRepository) {
    this.inboundRepository = inboundRepository;
  }

  @Transactional(readOnly = true)
  public Optional<CrmInboundEventEntity> find(String externalId) {
    if (externalId == null || externalId.isBlank()) {
      return Optional.empty();
    }
    return inboundRepository.findByProviderAndExternalId(PROVIDER, externalId);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public CrmInboundEventEntity insertReceived(
      String tenantId,
      String externalId,
      Map<String, Object> payload,
      Long campaignId,
      Long branchId) {
    CrmInboundEventEntity event = new CrmInboundEventEntity();
    event.setTenantId(tenantId);
    event.setProvider(PROVIDER);
    event.setExternalId(externalId);
    event.setStatus("RECEIVED");
    event.setCampaignId(campaignId);
    event.setBranchId(branchId);
    event.setPayloadJson(payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload));
    event.setAttemptCount(0);
    try {
      return inboundRepository.saveAndFlush(event);
    } catch (DataIntegrityViolationException ex) {
      throw ex;
    }
  }

  @Transactional
  public void markMapped(Long eventId, Long leadId) {
    CrmInboundEventEntity event = inboundRepository.findById(eventId).orElse(null);
    if (event == null) {
      return;
    }
    event.setLeadId(leadId);
    event.setStatus("MAPPED");
    event.setProcessedAt(Instant.now());
    event.setErrorMessage(null);
    inboundRepository.save(event);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markFailed(Long eventId, String message) {
    CrmInboundEventEntity event = inboundRepository.findById(eventId).orElse(null);
    if (event == null) {
      return;
    }
    event.setStatus("FAILED");
    event.setProcessedAt(Instant.now());
    event.setAttemptCount(event.getAttemptCount() + 1);
    event.setErrorMessage(clip(message));
    inboundRepository.save(event);
  }

  private static String clip(String message) {
    if (message == null || message.isBlank()) {
      return "failed";
    }
    String text = message.trim();
    return text.length() <= 500 ? text : text.substring(0, 500);
  }
}
