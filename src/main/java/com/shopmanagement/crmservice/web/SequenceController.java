package com.shopmanagement.crmservice.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.shopmanagement.crmservice.api.CrmSequenceApi.EnrollRequest;
import com.shopmanagement.crmservice.api.CrmSequenceApi.EnrollmentResponse;
import com.shopmanagement.crmservice.api.CrmSequenceApi.ProcessDueResponse;
import com.shopmanagement.crmservice.api.CrmSequenceApi.SequenceResponse;
import com.shopmanagement.crmservice.api.CrmSequenceApi.SequenceUpsert;
import com.shopmanagement.crmservice.entitlement.CrmEntitlementGuard;
import com.shopmanagement.crmservice.service.SequenceService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/crm/sequences")
public class SequenceController {

  private final SequenceService sequenceService;
  private final CrmEntitlementGuard entitlementGuard;

  public SequenceController(SequenceService sequenceService, CrmEntitlementGuard entitlementGuard) {
    this.sequenceService = sequenceService;
    this.entitlementGuard = entitlementGuard;
  }

  @GetMapping
  public List<SequenceResponse> list() {
    entitlementGuard.requireSequencesAccess();
    return sequenceService.list();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public SequenceResponse upsert(@Valid @RequestBody SequenceUpsert body) {
    entitlementGuard.requireSequencesAccess();
    return sequenceService.upsert(body);
  }

  @PostMapping("/ensure-welcome")
  public SequenceResponse ensureWelcome() {
    entitlementGuard.requireSequencesAccess();
    return sequenceService.ensureWelcomeSequence();
  }

  @PostMapping("/enrollments")
  @ResponseStatus(HttpStatus.CREATED)
  public EnrollmentResponse enroll(@Valid @RequestBody EnrollRequest body) {
    entitlementGuard.requireSequencesAccess();
    if (body != null && body.channel() != null) {
      entitlementGuard.requireChannelAccess(body.channel());
    }
    return sequenceService.enroll(body);
  }

  @GetMapping("/enrollments")
  public List<EnrollmentResponse> enrollments() {
    entitlementGuard.requireSequencesAccess();
    return sequenceService.listEnrollments();
  }

  @PostMapping("/process-due")
  public ProcessDueResponse processDue(@RequestParam(defaultValue = "20") int limit) {
    entitlementGuard.requireSequencesAccess();
    return sequenceService.processDue(limit);
  }

  @PostMapping("/enrollments/{id}/pause")
  public EnrollmentResponse pause(@org.springframework.web.bind.annotation.PathVariable Long id) {
    entitlementGuard.requireSequencesAccess();
    return sequenceService.pauseEnrollment(id);
  }

  @PostMapping("/enrollments/{id}/resume")
  public EnrollmentResponse resume(@org.springframework.web.bind.annotation.PathVariable Long id) {
    entitlementGuard.requireSequencesAccess();
    return sequenceService.resumeEnrollment(id);
  }

  @PostMapping("/enrollments/{id}/cancel")
  public EnrollmentResponse cancel(@org.springframework.web.bind.annotation.PathVariable Long id) {
    entitlementGuard.requireSequencesAccess();
    return sequenceService.cancelEnrollment(id);
  }

  @PostMapping("/enrollments/{id}/retry")
  public EnrollmentResponse retry(@org.springframework.web.bind.annotation.PathVariable Long id) {
    entitlementGuard.requireSequencesAccess();
    return sequenceService.retryEnrollment(id);
  }
}
