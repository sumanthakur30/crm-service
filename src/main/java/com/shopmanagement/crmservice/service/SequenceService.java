package com.shopmanagement.crmservice.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.shopmanagement.crmservice.api.CrmSequenceApi.EnrollRequest;
import com.shopmanagement.crmservice.api.CrmSequenceApi.EnrollmentResponse;
import com.shopmanagement.crmservice.api.CrmSequenceApi.ProcessDueResponse;
import com.shopmanagement.crmservice.api.CrmSequenceApi.SequenceResponse;
import com.shopmanagement.crmservice.api.CrmSequenceApi.SequenceStepResponse;
import com.shopmanagement.crmservice.api.CrmSequenceApi.SequenceStepUpsert;
import com.shopmanagement.crmservice.api.CrmSequenceApi.SequenceUpsert;
import com.shopmanagement.crmservice.integration.NotificationClient;
import com.shopmanagement.crmservice.persistence.entity.CrmSequenceEnrollmentEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmSequenceEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmSequenceStepEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmLeadRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmOpportunityRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmSequenceEnrollmentRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmSequenceRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmSequenceStepRepository;
import com.shopmanagement.crmservice.support.TenantIds;

@Service
public class SequenceService {

  private static final Set<String> CHANNELS = Set.of("WHATSAPP", "EMAIL", "SMS");

  private final CrmSequenceRepository sequenceRepository;
  private final CrmSequenceStepRepository stepRepository;
  private final CrmSequenceEnrollmentRepository enrollmentRepository;
  private final CrmLeadRepository leadRepository;
  private final CrmOpportunityRepository opportunityRepository;
  private final NotificationClient notificationClient;
  private final TimelineService timelineService;

  public SequenceService(
      CrmSequenceRepository sequenceRepository,
      CrmSequenceStepRepository stepRepository,
      CrmSequenceEnrollmentRepository enrollmentRepository,
      CrmLeadRepository leadRepository,
      CrmOpportunityRepository opportunityRepository,
      NotificationClient notificationClient,
      TimelineService timelineService) {
    this.sequenceRepository = sequenceRepository;
    this.stepRepository = stepRepository;
    this.enrollmentRepository = enrollmentRepository;
    this.leadRepository = leadRepository;
    this.opportunityRepository = opportunityRepository;
    this.notificationClient = notificationClient;
    this.timelineService = timelineService;
  }

  @Transactional
  public SequenceResponse upsert(SequenceUpsert body) {
    String tenantId = TenantIds.require();
    String code = body.code().trim().toUpperCase(Locale.ROOT);
    CrmSequenceEntity seq =
        sequenceRepository
            .findByTenantIdAndCodeAndDeletedAtIsNull(tenantId, code)
            .orElseGet(CrmSequenceEntity::new);
    if (seq.getId() == null) {
      seq.setTenantId(tenantId);
      seq.setCode(code);
    }
    seq.setName(body.name().trim());
    String channelDefault =
        body.channelDefault() == null || body.channelDefault().isBlank()
            ? "WHATSAPP"
            : body.channelDefault().trim().toUpperCase(Locale.ROOT);
    requireChannel(channelDefault);
    seq.setChannelDefault(channelDefault);
    seq.setActive(true);
    seq.touch();
    seq = sequenceRepository.save(seq);

    // null = leave steps unchanged; non-null (incl. empty) = full replace for visual builder.
    if (body.steps() != null) {
      for (CrmSequenceStepEntity existing :
          stepRepository.findByTenantIdAndSequenceIdAndDeletedAtIsNullOrderBySortOrderAsc(
              tenantId, seq.getId())) {
        existing.setDeletedAt(Instant.now());
        stepRepository.save(existing);
      }
      int order = 10;
      for (SequenceStepUpsert step : body.steps()) {
        CrmSequenceStepEntity entity = new CrmSequenceStepEntity();
        entity.setTenantId(tenantId);
        entity.setSequenceId(seq.getId());
        entity.setSortOrder(step.sortOrder() > 0 ? step.sortOrder() : order);
        entity.setDelayHours(Math.max(0, step.delayHours()));
        String ch = step.channel().trim().toUpperCase(Locale.ROOT);
        requireChannel(ch);
        entity.setChannel(ch);
        entity.setSubjectTemplate(blankToNull(step.subjectTemplate()));
        entity.setBodyTemplate(step.bodyTemplate().trim());
        entity.setActive(true);
        stepRepository.save(entity);
        order += 10;
      }
    }
    return toSequenceResponse(seq);
  }

  @Transactional
  public SequenceResponse ensureWelcomeSequence() {
    String tenantId = TenantIds.require();
    return sequenceRepository
        .findByTenantIdAndCodeAndDeletedAtIsNull(tenantId, "WELCOME_FOLLOWUP")
        .map(this::toSequenceResponse)
        .orElseGet(
            () ->
                upsert(
                    new SequenceUpsert(
                        "WELCOME_FOLLOWUP",
                        "Welcome follow-up",
                        "WHATSAPP",
                        List.of(
                            new SequenceStepUpsert(
                                10,
                                0,
                                "WHATSAPP",
                                null,
                                "Hi {{name}}, thanks for your interest. Reply YES to schedule a call."),
                            new SequenceStepUpsert(
                                20,
                                24,
                                "WHATSAPP",
                                null,
                                "Following up on our quote. Happy to share a GST quotation anytime."),
                            new SequenceStepUpsert(
                                30,
                                72,
                                "EMAIL",
                                "Quick follow-up from SugamFlow CRM",
                                "Hello {{name}},\n\nJust checking in — shall we send a formal quotation?\n\n— SugamFlow CRM")))));
  }

  @Transactional(readOnly = true)
  public List<SequenceResponse> list() {
    String tenantId = TenantIds.require();
    return sequenceRepository.findByTenantIdAndDeletedAtIsNullOrderByNameAsc(tenantId).stream()
        .map(this::toSequenceResponse)
        .toList();
  }

  @Transactional
  public EnrollmentResponse enroll(EnrollRequest body) {
    String tenantId = TenantIds.require();
    CrmSequenceEntity seq =
        sequenceRepository
            .findByTenantIdAndIdAndDeletedAtIsNull(tenantId, body.sequenceId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid sequenceId"));
    if (body.leadId() == null && body.opportunityId() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "leadId or opportunityId required");
    }
    if (body.leadId() != null) {
      leadRepository
          .findByTenantIdAndIdAndDeletedAtIsNull(tenantId, body.leadId())
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid leadId"));
    }
    if (body.opportunityId() != null) {
      opportunityRepository
          .findByTenantIdAndIdAndDeletedAtIsNull(tenantId, body.opportunityId())
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid opportunityId"));
    }
    String channel =
        body.channel() == null || body.channel().isBlank()
            ? seq.getChannelDefault()
            : body.channel().trim().toUpperCase(Locale.ROOT);
    requireChannel(channel);

    CrmSequenceEnrollmentEntity enr = new CrmSequenceEnrollmentEntity();
    enr.setTenantId(tenantId);
    enr.setSequenceId(seq.getId());
    enr.setLeadId(body.leadId());
    enr.setOpportunityId(body.opportunityId());
    enr.setRecipient(body.recipient().trim());
    enr.setChannel(channel);
    enr.setStatus("ACTIVE");
    enr.setCurrentStep(0);
    enr.setNextRunAt(Instant.now());
    enr = enrollmentRepository.save(enr);

    String relatedType = body.leadId() != null ? "LEAD" : "OPPORTUNITY";
    Long relatedId = body.leadId() != null ? body.leadId() : body.opportunityId();
    timelineService.recordEvent(
        relatedType,
        relatedId,
        "SEQUENCE_ENROLLED",
        "Enrolled in sequence " + seq.getCode(),
        Map.of("enrollmentId", enr.getId(), "sequenceId", seq.getId()));
    return toEnrollment(enr);
  }

  @Transactional(readOnly = true)
  public List<EnrollmentResponse> listEnrollments() {
    return enrollmentRepository
        .findByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc(TenantIds.require())
        .stream()
        .map(SequenceService::toEnrollment)
        .toList();
  }

  @Transactional
  public EnrollmentResponse pauseEnrollment(Long id) {
    return setEnrollmentStatus(id, "PAUSED", Set.of("ACTIVE"));
  }

  @Transactional
  public EnrollmentResponse resumeEnrollment(Long id) {
    CrmSequenceEnrollmentEntity enr = requireEnrollment(id);
    if (!"PAUSED".equalsIgnoreCase(enr.getStatus())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PAUSED enrollments can resume");
    }
    enr.setStatus("ACTIVE");
    if (enr.getNextRunAt() == null || enr.getNextRunAt().isBefore(Instant.now())) {
      enr.setNextRunAt(Instant.now());
    }
    enr.setLastError(null);
    enr.touch();
    return toEnrollment(enrollmentRepository.save(enr));
  }

  @Transactional
  public EnrollmentResponse cancelEnrollment(Long id) {
    return setEnrollmentStatus(id, "CANCELLED", Set.of("ACTIVE", "PAUSED", "FAILED"));
  }

  @Transactional
  public EnrollmentResponse retryEnrollment(Long id) {
    CrmSequenceEnrollmentEntity enr = requireEnrollment(id);
    if (!"FAILED".equalsIgnoreCase(enr.getStatus())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only FAILED enrollments can retry");
    }
    enr.setStatus("ACTIVE");
    enr.setNextRunAt(Instant.now());
    enr.setLastError(null);
    enr.touch();
    return toEnrollment(enrollmentRepository.save(enr));
  }

  private EnrollmentResponse setEnrollmentStatus(Long id, String status, Set<String> allowedFrom) {
    CrmSequenceEnrollmentEntity enr = requireEnrollment(id);
    if (!allowedFrom.contains(enr.getStatus() == null ? "" : enr.getStatus().toUpperCase(Locale.ROOT))) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Cannot set " + status + " from " + enr.getStatus());
    }
    enr.setStatus(status);
    enr.touch();
    return toEnrollment(enrollmentRepository.save(enr));
  }

  private CrmSequenceEnrollmentEntity requireEnrollment(Long id) {
    return enrollmentRepository
        .findByTenantIdAndIdAndDeletedAtIsNull(TenantIds.require(), id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Enrollment not found"));
  }

  @Transactional
  public ProcessDueResponse processDue(int limit) {
    String tenantId = TenantIds.require();
    Instant now = Instant.now();
    List<CrmSequenceEnrollmentEntity> due =
        enrollmentRepository
            .findByTenantIdAndStatusAndNextRunAtLessThanEqualAndDeletedAtIsNullOrderByNextRunAtAsc(
                tenantId, "ACTIVE", now);
    int processed = 0;
    int completed = 0;
    int failed = 0;
    int max = Math.max(1, Math.min(limit, 50));
    for (CrmSequenceEnrollmentEntity enr : due) {
      if (processed >= max) {
        break;
      }
      try {
        boolean done = advanceOneStep(tenantId, enr);
        processed++;
        if (done) {
          completed++;
        }
      } catch (RuntimeException ex) {
        failed++;
        enr.setLastError(ex.getMessage() == null ? "dispatch failed" : ex.getMessage().substring(0, Math.min(500, ex.getMessage().length())));
        enr.setStatus("FAILED");
        enr.touch();
        enrollmentRepository.save(enr);
      }
    }
    return new ProcessDueResponse(processed, completed, failed);
  }

  private boolean advanceOneStep(String tenantId, CrmSequenceEnrollmentEntity enr) {
    List<CrmSequenceStepEntity> steps =
        stepRepository.findByTenantIdAndSequenceIdAndDeletedAtIsNullOrderBySortOrderAsc(
            tenantId, enr.getSequenceId());
    if (enr.getCurrentStep() >= steps.size()) {
      enr.setStatus("COMPLETED");
      enr.setCompletedAt(Instant.now());
      enr.touch();
      enrollmentRepository.save(enr);
      return true;
    }
    CrmSequenceStepEntity step = steps.get(enr.getCurrentStep());
    String channel = step.getChannel() != null ? step.getChannel() : enr.getChannel();
    String subject =
        step.getSubjectTemplate() == null || step.getSubjectTemplate().isBlank()
            ? "SugamFlow CRM"
            : render(step.getSubjectTemplate(), enr);
    String body = render(step.getBodyTemplate(), enr);
    Map<String, String> vars = new LinkedHashMap<>();
    vars.put("subject", subject);
    vars.put("body", body);
    vars.put("recipient", Objects.toString(enr.getRecipient(), ""));
    vars.put("leadId", enr.getLeadId() == null ? "" : String.valueOf(enr.getLeadId()));
    Map<String, Object> delivery =
        notificationClient.queue(
            tenantId,
            channel,
            enr.getRecipient(),
            subject,
            body,
            "crm-seq-" + enr.getId() + "-step-" + enr.getCurrentStep(),
            notificationClient.properties().getSequenceStepTemplate(),
            vars);
    Map<String, Object> attrs = new LinkedHashMap<>(enr.getAttributes() == null ? Map.of() : enr.getAttributes());
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> history =
        attrs.get("deliveries") instanceof List<?> list
            ? new ArrayList<>((List<Map<String, Object>>) list)
            : new ArrayList<>();
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("step", enr.getCurrentStep());
    entry.put("at", Instant.now().toString());
    entry.put("delivery", delivery);
    history.add(entry);
    attrs.put("deliveries", history);
    enr.setAttributes(attrs);
    enr.setLastError(null);

    String delStatus = String.valueOf(delivery.getOrDefault("status", "UNKNOWN"));
    String eventType = "ERROR".equalsIgnoreCase(delStatus) ? "MESSAGE_FAILED" : "MESSAGE_QUEUED";
    if (enr.getLeadId() != null) {
      timelineService.recordEvent(
          "LEAD",
          enr.getLeadId(),
          eventType,
          "Sequence step " + enr.getCurrentStep() + " · " + channel + " · " + delStatus,
          Map.of(
              "channel",
              channel,
              "enrollmentId",
              enr.getId(),
              "step",
              enr.getCurrentStep(),
              "status",
              delStatus));
    } else if (enr.getOpportunityId() != null) {
      timelineService.recordEvent(
          "OPPORTUNITY",
          enr.getOpportunityId(),
          eventType,
          "Sequence step " + enr.getCurrentStep() + " · " + channel + " · " + delStatus,
          Map.of(
              "channel",
              channel,
              "enrollmentId",
              enr.getId(),
              "step",
              enr.getCurrentStep(),
              "status",
              delStatus));
    }

    int nextIndex = enr.getCurrentStep() + 1;
    enr.setCurrentStep(nextIndex);
    if (nextIndex >= steps.size()) {
      enr.setStatus("COMPLETED");
      enr.setCompletedAt(Instant.now());
      enr.touch();
      enrollmentRepository.save(enr);
      return true;
    }
    int delay = steps.get(nextIndex).getDelayHours();
    enr.setNextRunAt(Instant.now().plus(delay, ChronoUnit.HOURS));
    enr.touch();
    enrollmentRepository.save(enr);
    return false;
  }

  private SequenceResponse toSequenceResponse(CrmSequenceEntity seq) {
    List<SequenceStepResponse> steps =
        stepRepository
            .findByTenantIdAndSequenceIdAndDeletedAtIsNullOrderBySortOrderAsc(
                seq.getTenantId(), seq.getId())
            .stream()
            .map(
                s ->
                    new SequenceStepResponse(
                        s.getId(),
                        s.getSortOrder(),
                        s.getDelayHours(),
                        s.getChannel(),
                        s.getSubjectTemplate(),
                        s.getBodyTemplate()))
            .toList();
    return new SequenceResponse(
        seq.getId(), seq.getCode(), seq.getName(), seq.getChannelDefault(), seq.isActive(), steps);
  }

  private static EnrollmentResponse toEnrollment(CrmSequenceEnrollmentEntity e) {
    return new EnrollmentResponse(
        e.getId(),
        e.getSequenceId(),
        e.getLeadId(),
        e.getOpportunityId(),
        e.getRecipient(),
        e.getChannel(),
        e.getStatus(),
        e.getCurrentStep(),
        e.getNextRunAt(),
        e.getLastError(),
        e.getAttributes(),
        e.getCompletedAt());
  }

  private static void requireChannel(String channel) {
    if (!CHANNELS.contains(channel)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid channel: " + channel);
    }
  }

  private static String blankToNull(String v) {
    return v == null || v.isBlank() ? null : v.trim();
  }

  private static String render(String template, CrmSequenceEnrollmentEntity enr) {
    String name = enr.getRecipient();
    return template
        .replace("{{name}}", name)
        .replace("{{recipient}}", enr.getRecipient())
        .replace("{{leadId}}", enr.getLeadId() == null ? "" : String.valueOf(enr.getLeadId()));
  }
}
