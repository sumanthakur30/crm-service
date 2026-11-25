package com.shopmanagement.crmservice.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.shopmanagement.crmservice.api.CrmLeadApi.LeadResponse;
import com.shopmanagement.crmservice.api.CrmLeadApi.NoteRequest;
import com.shopmanagement.crmservice.persistence.entity.CrmLeadEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmStageEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmLeadRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmStageRepository;
import com.shopmanagement.crmservice.support.TenantIds;

/** One submit writes the call, the note, the next follow-up, and an optional stage change. */
@Service
public class LeadOutcomeService {

  static final Set<String> OUTCOMES =
      Set.of("CONNECTED", "NO_ANSWER", "BUSY", "SWITCHED_OFF", "INVALID_NUMBER", "CALLBACK_REQUESTED");

  private final CrmLeadRepository leadRepository;
  private final CrmStageRepository stageRepository;
  private final CrmLeadService leadService;
  private final OpsService opsService;
  private final TimelineService timelineService;
  private final FollowUpTaskService followUpTaskService;

  public LeadOutcomeService(
      CrmLeadRepository leadRepository,
      CrmStageRepository stageRepository,
      CrmLeadService leadService,
      OpsService opsService,
      TimelineService timelineService,
      FollowUpTaskService followUpTaskService) {
    this.leadRepository = leadRepository;
    this.stageRepository = stageRepository;
    this.leadService = leadService;
    this.opsService = opsService;
    this.timelineService = timelineService;
    this.followUpTaskService = followUpTaskService;
  }

  @Transactional
  public LeadResponse log(Long leadId, String outcome, String note, String nextFollowUpDate, Long stageId) {
    String tenantId = TenantIds.require();
    String code =
        outcome == null
            ? ""
            : outcome.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    if (!OUTCOMES.contains(code)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Outcome must be Connected, no answer, busy, switched off, invalid number, or callback requested");
    }
    CrmLeadEntity lead =
        leadRepository
            .findByTenantIdAndIdAndDeletedAtIsNull(tenantId, leadId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead not found"));
    CrmStageEntity stage = resolveStage(tenantId, lead, stageId);
    String followUp = normalizeDate(nextFollowUpDate);
    if (followUp == null && !stage.isWon() && !stage.isLost()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Next follow-up is required unless the stage is won or lost");
    }
    if (stageId != null && !stageId.equals(lead.getStageId())) {
      leadService.moveStage(leadId, stageId);
      lead =
          leadRepository
              .findByTenantIdAndIdAndDeletedAtIsNull(tenantId, leadId)
              .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead not found"));
    }
    opsService.logCall(
        Map.of(
            "leadId", leadId,
            "phone", lead.getPhone() == null ? "" : lead.getPhone(),
            "outcome", code,
            "direction", "OUTBOUND",
            "durationSec", 0,
            "notes", note == null ? "" : note));
    if (note != null && !note.isBlank()) {
      timelineService.addLeadNote(leadId, new NoteRequest(note.trim()));
    }
    applyFollowUp(lead, followUp);
    if ("CONNECTED".equals(code) && lead.getFirstResponseAt() == null) {
      lead.setFirstResponseAt(Instant.now());
    }
    lead.touch();
    leadRepository.save(lead);
    followUpTaskService.syncLead(lead);
    return leadService.get(leadId);
  }

  private CrmStageEntity resolveStage(String tenantId, CrmLeadEntity lead, Long stageId) {
    Long id = stageId != null ? stageId : lead.getStageId();
    CrmStageEntity stage =
        stageRepository
            .findByTenantIdAndIdAndDeletedAtIsNull(tenantId, id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid stageId"));
    if (!lead.getPipelineId().equals(stage.getPipelineId())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "stageId does not belong to pipeline");
    }
    return stage;
  }

  private static void applyFollowUp(CrmLeadEntity lead, String followUp) {
    Map<String, Object> attributes = new LinkedHashMap<>();
    if (lead.getAttributes() != null) {
      attributes.putAll(lead.getAttributes());
    }
    Map<String, Object> profile = new LinkedHashMap<>();
    Object raw = attributes.get("profile");
    if (raw instanceof Map<?, ?> map) {
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        profile.put(String.valueOf(entry.getKey()), entry.getValue());
      }
    }
    if (followUp == null) {
      profile.remove("nextFollowUpDate");
    } else {
      profile.put("nextFollowUpDate", followUp);
    }
    attributes.put("profile", profile);
    lead.setAttributes(attributes);
  }

  private static String normalizeDate(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return LocalDate.parse(raw.trim()).toString();
    } catch (DateTimeParseException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Next follow-up must be a date");
    }
  }
}
