package com.shopmanagement.crmservice.web;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.shopmanagement.crmservice.api.CrmLeadApi.AssignRequest;
import com.shopmanagement.crmservice.api.CrmLeadApi.BulkAssignRequest;
import com.shopmanagement.crmservice.api.CrmLeadApi.ImportPreview;
import com.shopmanagement.crmservice.api.CrmLeadApi.ImportResult;
import com.shopmanagement.crmservice.api.CrmLeadApi.LeadResponse;
import com.shopmanagement.crmservice.api.CrmLeadApi.OutcomeRequest;
import com.shopmanagement.crmservice.api.CrmLeadApi.LeadStatusPatch;
import com.shopmanagement.crmservice.api.CrmLeadApi.LeadUpsert;
import com.shopmanagement.crmservice.api.CrmPartyConvertApi.ConvertToCrmRequest;
import com.shopmanagement.crmservice.api.CrmPartyConvertApi.ConvertToCrmResponse;
import com.shopmanagement.crmservice.entitlement.CrmEntitlementGuard;
import com.shopmanagement.crmservice.service.AssignmentService;
import com.shopmanagement.crmservice.service.CrmLeadService;
import com.shopmanagement.crmservice.service.CrmPartyConvertService;
import com.shopmanagement.crmservice.service.LeadConvertService;
import com.shopmanagement.crmservice.service.LeadImportService;
import com.shopmanagement.crmservice.service.LeadOutcomeService;
import com.shopmanagement.crmservice.service.TimelineService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/crm/leads")
public class CrmLeadController {

  private final CrmLeadService leadService;
  private final AssignmentService assignmentService;
  private final LeadImportService importService;
  private final TimelineService timelineService;
  private final LeadConvertService leadConvertService;
  private final CrmPartyConvertService partyConvertService;
  private final CrmEntitlementGuard entitlementGuard;
  private final LeadOutcomeService leadOutcomeService;

  public CrmLeadController(
      CrmLeadService leadService,
      AssignmentService assignmentService,
      LeadImportService importService,
      TimelineService timelineService,
      LeadConvertService leadConvertService,
      CrmPartyConvertService partyConvertService,
      CrmEntitlementGuard entitlementGuard,
      LeadOutcomeService leadOutcomeService) {
    this.leadService = leadService;
    this.assignmentService = assignmentService;
    this.importService = importService;
    this.timelineService = timelineService;
    this.leadConvertService = leadConvertService;
    this.partyConvertService = partyConvertService;
    this.entitlementGuard = entitlementGuard;
    this.leadOutcomeService = leadOutcomeService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public LeadResponse create(@Valid @RequestBody LeadUpsert body) {
    entitlementGuard.requireCrmAccess();
    return leadService.create(body);
  }

  @PutMapping("/{id}")
  public LeadResponse update(@PathVariable Long id, @Valid @RequestBody LeadUpsert body) {
    entitlementGuard.requireCrmAccess();
    return leadService.update(id, body);
  }

  @PatchMapping("/{id}/status")
  public LeadResponse patchStatus(@PathVariable Long id, @Valid @RequestBody LeadStatusPatch body) {
    entitlementGuard.requireCrmAccess();
    return leadService.patchStatus(id, body);
  }

  @GetMapping("/{id}")
  public LeadResponse get(@PathVariable Long id) {
    entitlementGuard.requireCrmAccess();
    return leadService.get(id);
  }

  @GetMapping
  public Page<LeadResponse> list(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) Long stageId,
      @RequestParam(required = false) Long pipelineId,
      @RequestParam(required = false) String ownerUserId,
      @RequestParam(required = false) String sourceCode,
      @RequestParam(required = false) String followUp,
      @RequestParam(required = false) String unassigned,
      @RequestParam(required = false) String course,
      @RequestParam(required = false) String q,
      Pageable pageable) {
    entitlementGuard.requireCrmAccess();
    return leadService.search(
        status, stageId, pipelineId, ownerUserId, sourceCode, unassigned, followUp, course, q, pageable);
  }

  @PostMapping("/bulk-assign")
  public java.util.Map<String, Object> bulkAssign(@RequestBody BulkAssignRequest body) {
    entitlementGuard.requireCrmAccess();
    BulkAssignRequest request = body == null ? new BulkAssignRequest(java.util.List.of(), null, null, null) : body;
    int assigned =
        assignmentService.bulkAssign(
            request.leadIds(),
            new AssignRequest(
                request.mode() == null ? "MANUAL" : request.mode(),
                request.ownerUserId(),
                request.teamId()));
    return java.util.Map.of("assigned", assigned);
  }

  @PostMapping("/{id}/outcomes")
  public LeadResponse logOutcome(@PathVariable Long id, @RequestBody OutcomeRequest body) {
    entitlementGuard.requireCrmAccess();
    OutcomeRequest request = body == null ? new OutcomeRequest(null, null, null, null) : body;
    return leadOutcomeService.log(
        id, request.outcome(), request.note(), request.nextFollowUpDate(), request.stageId());
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id) {
    entitlementGuard.requireCrmAccess();
    leadService.delete(id);
  }

  @PostMapping("/{id}/assign")
  public LeadResponse assign(@PathVariable Long id, @RequestBody AssignRequest body) {
    entitlementGuard.requireCrmAccess();
    return assignmentService.assign(id, body == null ? new AssignRequest("ROUND_ROBIN", null, null) : body);
  }

  @PostMapping("/{id}/stage/{stageId}")
  public LeadResponse moveStage(@PathVariable Long id, @PathVariable Long stageId) {
    entitlementGuard.requireCrmAccess();
    return leadService.moveStage(id, stageId);
  }

  @GetMapping("/{id}/timeline")
  public java.util.List<com.shopmanagement.crmservice.api.CrmLeadApi.TimelineItem> timeline(
      @PathVariable Long id) {
    entitlementGuard.requireCrmAccess();
    return timelineService.leadTimeline(id);
  }

  @PostMapping("/{id}/notes")
  @ResponseStatus(HttpStatus.CREATED)
  public com.shopmanagement.crmservice.api.CrmLeadApi.NoteResponse addNote(
      @PathVariable Long id, @Valid @RequestBody com.shopmanagement.crmservice.api.CrmLeadApi.NoteRequest body) {
    entitlementGuard.requireCrmAccess();
    return timelineService.addLeadNote(id, body);
  }

  @PostMapping("/{id}/convert")
  public java.util.Map<String, Object> convert(
      @PathVariable Long id, @RequestParam String targetSystem) {
    entitlementGuard.requireCrmAccess();
    return leadConvertService.convert(id, targetSystem);
  }

  /** CRM party convert (account/contact/opportunity) — separate from ERP convert. */
  @PostMapping("/{id}/convert-to-crm")
  public ConvertToCrmResponse convertToCrm(
      @PathVariable Long id, @Valid @RequestBody ConvertToCrmRequest body) {
    entitlementGuard.requireCrmAccess();
    return partyConvertService.convert(id, body);
  }

  @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ImportResult importLeads(
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "assignRoundRobin", defaultValue = "false") boolean assignRoundRobin,
      @RequestParam(value = "teamId", required = false) String teamId) {
    return importService.importFile(file, assignRoundRobin, teamId);
  }

  @PostMapping(value = "/imports/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ImportPreview previewImport(@RequestParam("file") MultipartFile file) {
    return importService.preview(file);
  }

  @PostMapping(value = "/imports/commit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ImportResult commitImport(
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "assignRoundRobin", defaultValue = "false") boolean assignRoundRobin,
      @RequestParam(value = "teamId", required = false) String teamId,
      @RequestParam(value = "columnMap", required = false) String columnMap) {
    return importService.importMapped(file, assignRoundRobin, teamId, parseColumnMap(columnMap));
  }

  private static java.util.Map<String, String> parseColumnMap(String columnMap) {
    if (columnMap == null || columnMap.isBlank()) {
      return java.util.Map.of();
    }
    try {
      return new com.fasterxml.jackson.databind.ObjectMapper()
          .readValue(columnMap, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, String>>() {});
    } catch (Exception ex) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.BAD_REQUEST, "columnMap must be a JSON object");
    }
  }
}
