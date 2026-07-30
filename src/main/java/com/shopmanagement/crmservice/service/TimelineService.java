package com.shopmanagement.crmservice.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.shopmanagement.crmservice.api.CrmLeadApi.NoteRequest;
import com.shopmanagement.crmservice.api.CrmLeadApi.NoteResponse;
import com.shopmanagement.crmservice.api.CrmLeadApi.TimelineItem;
import com.shopmanagement.crmservice.persistence.entity.CrmNoteEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmTimelineEventEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmLeadRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmNoteRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmTimelineEventRepository;
import com.shopmanagement.crmservice.support.TenantIds;

@Service
public class TimelineService {

  private final CrmNoteRepository noteRepository;
  private final CrmTimelineEventRepository timelineRepository;
  private final CrmLeadRepository leadRepository;

  public TimelineService(
      CrmNoteRepository noteRepository,
      CrmTimelineEventRepository timelineRepository,
      CrmLeadRepository leadRepository) {
    this.noteRepository = noteRepository;
    this.timelineRepository = timelineRepository;
    this.leadRepository = leadRepository;
  }

  @Transactional
  public void recordEvent(String relatedType, Long relatedId, String eventType, String summary, Map<String, Object> payload) {
    String tenantId = TenantIds.require();
    CrmTimelineEventEntity event = new CrmTimelineEventEntity();
    event.setTenantId(tenantId);
    event.setRelatedType(relatedType);
    event.setRelatedId(relatedId);
    event.setEventType(eventType);
    event.setSummary(summary);
    event.setPayloadJson(payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload));
    event.setActorUserId(TenantIds.currentUserOrNull());
    event.setOccurredAt(Instant.now());
    timelineRepository.save(event);
  }

  @Transactional
  public NoteResponse addLeadNote(Long leadId, NoteRequest body) {
    String tenantId = TenantIds.require();
    requireLead(tenantId, leadId);
    if (body == null || body.body() == null || body.body().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Note body is required");
    }
    CrmNoteEntity note = new CrmNoteEntity();
    note.setTenantId(tenantId);
    note.setRelatedType("LEAD");
    note.setRelatedId(leadId);
    note.setBody(body.body().trim());
    note.setAuthorUserId(TenantIds.currentUserOrNull());
    note = noteRepository.save(note);
    recordEvent(
        "LEAD",
        leadId,
        "NOTE_ADDED",
        "Note added",
        Map.of("noteId", note.getId()));
    return toNote(note);
  }

  @Transactional(readOnly = true)
  public List<TimelineItem> leadTimeline(Long leadId) {
    String tenantId = TenantIds.require();
    requireLead(tenantId, leadId);

    List<TimelineItem> items = new ArrayList<>();
    for (CrmNoteEntity note :
        noteRepository.findByTenantIdAndRelatedTypeAndRelatedIdAndDeletedAtIsNullOrderByCreatedAtDesc(
            tenantId, "LEAD", leadId)) {
      items.add(
          new TimelineItem(
              "NOTE",
              note.getId(),
              "NOTE",
              note.getBody(),
              note.getAuthorUserId(),
              note.getCreatedAt(),
              Map.of()));
    }
    for (CrmTimelineEventEntity event :
        timelineRepository.findByTenantIdAndRelatedTypeAndRelatedIdOrderByOccurredAtDesc(
            tenantId, "LEAD", leadId)) {
      items.add(
          new TimelineItem(
              "EVENT",
              event.getId(),
              event.getEventType(),
              event.getSummary(),
              event.getActorUserId(),
              event.getOccurredAt(),
              event.getPayloadJson()));
    }
    items.sort(Comparator.comparing(TimelineItem::occurredAt, Comparator.nullsLast(Comparator.reverseOrder())));
    return items;
  }

  private void requireLead(String tenantId, Long leadId) {
    leadRepository
        .findByTenantIdAndIdAndDeletedAtIsNull(tenantId, leadId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead not found"));
  }

  private static NoteResponse toNote(CrmNoteEntity note) {
    return new NoteResponse(
        note.getId(),
        note.getRelatedType(),
        note.getRelatedId(),
        note.getBody(),
        note.getAuthorUserId(),
        note.getCreatedAt());
  }
}
