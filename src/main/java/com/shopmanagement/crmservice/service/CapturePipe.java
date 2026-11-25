package com.shopmanagement.crmservice.service;

import java.util.Map;
import java.util.function.Function;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.shopmanagement.crmservice.api.CrmLeadApi.LeadResponse;
import com.shopmanagement.crmservice.persistence.entity.CrmInboundEventEntity;

/**
 * Writes the raw event, then the lead. The same idempotency key returns the first lead and does
 * not run the create function again, so a second touch is not inserted.
 */
@Service
public class CapturePipe {

  public record CaptureResult(LeadResponse lead, boolean duplicate) {}

  private final InboundIdempotencyService idempotency;
  private final CaptureJobService jobs;
  private final CrmLeadService leadService;

  public CapturePipe(
      InboundIdempotencyService idempotency, CaptureJobService jobs, CrmLeadService leadService) {
    this.idempotency = idempotency;
    this.jobs = jobs;
    this.leadService = leadService;
  }

  @Transactional
  public CaptureResult run(
      String tenantId,
      String idempotencyKey,
      Map<String, Object> payload,
      Long campaignId,
      Long branchId,
      Function<CrmInboundEventEntity, LeadResponse> create) {
    String key = blank(idempotencyKey) ? null : clipKey(idempotencyKey);
    if (key != null) {
      var existing = idempotency.find(key);
      if (existing.isPresent()) {
        return replay(existing.get());
      }
      CrmInboundEventEntity event;
      try {
        event = idempotency.insertReceived(tenantId, key, payload, campaignId, branchId);
      } catch (DataIntegrityViolationException ex) {
        return replay(idempotency.find(key).orElseThrow(() -> ex));
      }
      return finish(tenantId, event, create);
    }
    CrmInboundEventEntity event = idempotency.insertReceived(tenantId, null, payload, campaignId, branchId);
    return finish(tenantId, event, create);
  }

  private CaptureResult finish(
      String tenantId, CrmInboundEventEntity event, Function<CrmInboundEventEntity, LeadResponse> create) {
    try {
      LeadResponse lead = create.apply(event);
      idempotency.markMapped(event.getId(), lead.id());
      jobs.done(tenantId, event.getId());
      return new CaptureResult(lead, false);
    } catch (RuntimeException ex) {
      idempotency.markFailed(event.getId(), ex.getMessage());
      throw ex;
    }
  }

  private CaptureResult replay(CrmInboundEventEntity event) {
    if ("FAILED".equalsIgnoreCase(event.getStatus())) {
      String message =
          event.getErrorMessage() == null || event.getErrorMessage().isBlank()
              ? "This submission already failed"
              : event.getErrorMessage();
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
    if (event.getLeadId() == null) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "This submission is already being saved. Retry with the same Idempotency-Key.");
    }
    return new CaptureResult(leadService.get(event.getLeadId()), true);
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private static String clipKey(String key) {
    String text = key.trim();
    return text.length() <= 128 ? text : text.substring(0, 128);
  }
}
