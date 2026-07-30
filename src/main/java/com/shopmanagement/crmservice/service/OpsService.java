package com.shopmanagement.crmservice.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.shopmanagement.crmservice.persistence.entity.CrmApprovalEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmCalendarEventEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmCallLogEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmOpportunityEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmApprovalRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmCalendarEventRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmCallLogRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmOpportunityRepository;
import com.shopmanagement.crmservice.support.TenantIds;

@Service
public class OpsService {

  private final CrmCalendarEventRepository calendarRepository;
  private final CrmCallLogRepository callLogRepository;
  private final CrmApprovalRepository approvalRepository;
  private final CrmOpportunityRepository opportunityRepository;
  private final TimelineService timelineService;
  private final BehaviorScoringService scoringService;
  private final QuotationService quotationService;

  public OpsService(
      CrmCalendarEventRepository calendarRepository,
      CrmCallLogRepository callLogRepository,
      CrmApprovalRepository approvalRepository,
      CrmOpportunityRepository opportunityRepository,
      TimelineService timelineService,
      BehaviorScoringService scoringService,
      QuotationService quotationService) {
    this.calendarRepository = calendarRepository;
    this.callLogRepository = callLogRepository;
    this.approvalRepository = approvalRepository;
    this.opportunityRepository = opportunityRepository;
    this.timelineService = timelineService;
    this.scoringService = scoringService;
    this.quotationService = quotationService;
  }

  @Transactional
  public Map<String, Object> createCalendarEvent(Map<String, Object> body) {
    String tenantId = TenantIds.require();
    CrmCalendarEventEntity e = new CrmCalendarEventEntity();
    e.setTenantId(tenantId);
    e.setRelatedType(req(body, "relatedType").toUpperCase(Locale.ROOT));
    e.setRelatedId(asLong(body.get("relatedId")));
    e.setTitle(req(body, "title"));
    e.setStartsAt(Instant.parse(String.valueOf(body.get("startsAt"))));
    if (body.get("endsAt") != null) {
      e.setEndsAt(Instant.parse(String.valueOf(body.get("endsAt"))));
    }
    e.setOwnerUserId(str(body.get("ownerUserId")));
    e.setLocation(str(body.get("location")));
    e.setStatus("SCHEDULED");
    e = calendarRepository.save(e);
    timelineService.recordEvent(
        e.getRelatedType(),
        e.getRelatedId(),
        "MEETING_BOOKED",
        "Meeting: " + e.getTitle(),
        Map.of("calendarEventId", e.getId()));
    if ("LEAD".equals(e.getRelatedType())) {
      scoringService.applyEvent(e.getRelatedId(), "MEETING_BOOKED", e.getTitle(), Map.of("calendarEventId", e.getId()));
    }
    return toCal(e);
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listCalendar(Instant from) {
    Instant start = from == null ? Instant.now().minusSeconds(86400) : from;
    return calendarRepository
        .findByTenantIdAndStartsAtGreaterThanEqualAndDeletedAtIsNullOrderByStartsAtAsc(
            TenantIds.require(), start)
        .stream()
        .map(OpsService::toCal)
        .toList();
  }

  @Transactional
  public Map<String, Object> logCall(Map<String, Object> body) {
    String tenantId = TenantIds.require();
    CrmCallLogEntity call = new CrmCallLogEntity();
    call.setTenantId(tenantId);
    Long leadId = body.get("leadId") == null ? null : asLong(body.get("leadId"));
    call.setLeadId(leadId);
    call.setDirection(
        body.get("direction") == null
            ? "OUTBOUND"
            : String.valueOf(body.get("direction")).toUpperCase(Locale.ROOT));
    call.setPhone(str(body.get("phone")));
    call.setDurationSec(body.get("durationSec") == null ? 0 : ((Number) body.get("durationSec")).intValue());
    call.setOutcome(str(body.get("outcome")));
    call.setProvider(str(body.get("provider")));
    call.setProviderRef(str(body.get("providerRef")));
    call.setNotes(str(body.get("notes")));
    if (body.get("occurredAt") != null) {
      call.setOccurredAt(Instant.parse(String.valueOf(body.get("occurredAt"))));
    }
    call = callLogRepository.save(call);
    if (leadId != null) {
      timelineService.recordEvent(
          "LEAD",
          leadId,
          "CALL_LOGGED",
          "Call " + call.getDirection() + " · " + (call.getOutcome() == null ? "-" : call.getOutcome()),
          Map.of("callId", call.getId()));
      if ("CONNECTED".equalsIgnoreCase(call.getOutcome()) || call.getDurationSec() > 0) {
        scoringService.applyEvent(leadId, "CALL_CONNECTED", "Call connected", Map.of("callId", call.getId()));
      }
    }
    return toCall(call);
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> callsForLead(Long leadId) {
    return callLogRepository
        .findByTenantIdAndLeadIdOrderByOccurredAtDesc(TenantIds.require(), leadId)
        .stream()
        .map(OpsService::toCall)
        .toList();
  }

  @Transactional
  public Map<String, Object> requestApproval(Map<String, Object> body) {
    CrmApprovalEntity a = new CrmApprovalEntity();
    a.setTenantId(TenantIds.require());
    a.setObjectType(req(body, "objectType").toUpperCase(Locale.ROOT));
    a.setObjectId(asLong(body.get("objectId")));
    a.setTitle(req(body, "title"));
    a.setStatus("PENDING");
    a.setRequestedBy(str(body.get("requestedBy")) != null ? str(body.get("requestedBy")) : TenantIds.currentUserOrNull());
    a = approvalRepository.save(a);
    return toApproval(a);
  }

  @Transactional
  public Map<String, Object> decideApproval(Long id, boolean approve, String note) {
    CrmApprovalEntity a =
        approvalRepository
            .findById(id)
            .filter(x -> TenantIds.require().equals(x.getTenantId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Approval not found"));
    if (!"PENDING".equals(a.getStatus())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Approval already decided");
    }
    a.setStatus(approve ? "APPROVED" : "REJECTED");
    a.setDecidedBy(TenantIds.currentUserOrNull());
    a.setDecisionNote(note);
    a.setDecidedAt(Instant.now());
    a.setUpdatedAt(Instant.now());
    a = approvalRepository.save(a);
    if ("QUOTATION".equalsIgnoreCase(a.getObjectType())) {
      quotationService.applyDiscountApprovalDecision(a.getObjectId(), approve, a.getId());
    }
    return toApproval(a);
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listApprovals(String status) {
    String st = status == null || status.isBlank() ? "PENDING" : status.trim().toUpperCase(Locale.ROOT);
    return approvalRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(TenantIds.require(), st).stream()
        .map(OpsService::toApproval)
        .toList();
  }

  @Transactional(readOnly = true)
  public Map<String, Object> forecast() {
    String tenantId = TenantIds.require();
    List<CrmOpportunityEntity> open =
        opportunityRepository
            .search(tenantId, "OPEN", null, null, PageRequest.of(0, 500))
            .getContent();
    BigDecimal weighted = BigDecimal.ZERO;
    BigDecimal pipeline = BigDecimal.ZERO;
    List<Map<String, Object>> rows = new ArrayList<>();
    for (CrmOpportunityEntity o : open) {
      BigDecimal amt = o.getAmount() == null ? BigDecimal.ZERO : o.getAmount();
      BigDecimal w =
          amt.multiply(BigDecimal.valueOf(o.getProbability()))
              .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
      weighted = weighted.add(w);
      pipeline = pipeline.add(amt);
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("id", o.getId());
      row.put("name", o.getName());
      row.put("amount", amt);
      row.put("probability", o.getProbability());
      row.put("weightedAmount", w);
      row.put("expectedCloseDate", o.getExpectedCloseDate());
      rows.add(row);
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("openCount", open.size());
    out.put("pipelineAmount", pipeline);
    out.put("weightedForecast", weighted);
    out.put("deals", rows);
    return out;
  }

  private static Map<String, Object> toCal(CrmCalendarEventEntity e) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", e.getId());
    m.put("relatedType", e.getRelatedType());
    m.put("relatedId", e.getRelatedId());
    m.put("title", e.getTitle());
    m.put("startsAt", e.getStartsAt());
    m.put("endsAt", e.getEndsAt());
    m.put("ownerUserId", e.getOwnerUserId());
    m.put("status", e.getStatus());
    return m;
  }

  private static Map<String, Object> toCall(CrmCallLogEntity c) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", c.getId());
    m.put("leadId", c.getLeadId());
    m.put("direction", c.getDirection());
    m.put("phone", c.getPhone());
    m.put("durationSec", c.getDurationSec());
    m.put("outcome", c.getOutcome());
    m.put("occurredAt", c.getOccurredAt());
    return m;
  }

  private static Map<String, Object> toApproval(CrmApprovalEntity a) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", a.getId());
    m.put("objectType", a.getObjectType());
    m.put("objectId", a.getObjectId());
    m.put("title", a.getTitle());
    m.put("status", a.getStatus());
    m.put("requestedBy", a.getRequestedBy());
    m.put("decidedBy", a.getDecidedBy());
    m.put("decisionNote", a.getDecisionNote());
    m.put("createdAt", a.getCreatedAt());
    return m;
  }

  private static String req(Map<String, Object> body, String key) {
    Object v = body.get(key);
    if (v == null || String.valueOf(v).isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " required");
    }
    return String.valueOf(v).trim();
  }

  private static String str(Object v) {
    return v == null || String.valueOf(v).isBlank() ? null : String.valueOf(v).trim();
  }

  private static Long asLong(Object v) {
    if (v == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
    }
    return ((Number) v).longValue();
  }
}
