package com.shopmanagement.crmservice.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.shopmanagement.crmservice.api.CrmLeadApi.LeadResponse;
import com.shopmanagement.crmservice.api.CrmLeadApi.LeadStatusPatch;
import com.shopmanagement.crmservice.api.CrmLeadApi.LeadUpsert;
import com.shopmanagement.crmservice.persistence.entity.CrmLeadEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmPipelineEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmStageEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmAccountRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmCampaignRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmContactRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmLeadRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmPipelineRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmStageRepository;
import com.shopmanagement.crmservice.security.CrmAccessScope;
import com.shopmanagement.crmservice.security.CrmRecordScopeService;
import com.shopmanagement.crmservice.support.TenantIds;

@Service
public class CrmLeadService {

  private static final Set<String> STATUSES = Set.of("OPEN", "QUALIFIED", "CONVERTED", "LOST", "DUPLICATE");
  private static final Set<String> PRIORITIES = Set.of("LOW", "MEDIUM", "HIGH", "HOT");

  private final CrmLeadRepository leadRepository;
  private final CrmPipelineRepository pipelineRepository;
  private final CrmStageRepository stageRepository;
  private final CrmCampaignRepository campaignRepository;
  private final CrmAccountRepository accountRepository;
  private final CrmContactRepository contactRepository;
  private final WorkspaceBootstrapService workspaceBootstrapService;
  private final TimelineService timelineService;
  private final StageAutomationService stageAutomationService;
  private final CrmRecordScopeService recordScopeService;

  public CrmLeadService(
      CrmLeadRepository leadRepository,
      CrmPipelineRepository pipelineRepository,
      CrmStageRepository stageRepository,
      CrmCampaignRepository campaignRepository,
      CrmAccountRepository accountRepository,
      CrmContactRepository contactRepository,
      WorkspaceBootstrapService workspaceBootstrapService,
      TimelineService timelineService,
      StageAutomationService stageAutomationService,
      CrmRecordScopeService recordScopeService) {
    this.leadRepository = leadRepository;
    this.pipelineRepository = pipelineRepository;
    this.stageRepository = stageRepository;
    this.campaignRepository = campaignRepository;
    this.accountRepository = accountRepository;
    this.contactRepository = contactRepository;
    this.workspaceBootstrapService = workspaceBootstrapService;
    this.timelineService = timelineService;
    this.stageAutomationService = stageAutomationService;
    this.recordScopeService = recordScopeService;
  }

  @Transactional
  public LeadResponse create(LeadUpsert body) {
    String tenantId = TenantIds.require();
    CrmPipelineEntity pipeline = resolvePipeline(tenantId, body.pipelineId());
    CrmStageEntity stage = resolveStage(tenantId, pipeline.getId(), body.stageId());

    CrmLeadEntity lead = new CrmLeadEntity();
    lead.setTenantId(tenantId);
    apply(lead, body, pipeline.getId(), stage.getId(), true);
    if (lead.getOwnerUserId() == null) {
      lead.setOwnerUserId(TenantIds.currentUserOrNull());
    }
    lead = leadRepository.save(lead);
    timelineService.recordEvent(
        "LEAD",
        lead.getId(),
        "LEAD_CREATED",
        "Lead created: " + lead.getTitle(),
        Map.of("stageId", lead.getStageId(), "status", lead.getStatus()));
    return toResponse(lead);
  }

  @Transactional
  public LeadResponse update(Long id, LeadUpsert body) {
    String tenantId = TenantIds.require();
    CrmLeadEntity lead = requireLead(tenantId, id);
    recordScopeService.assertCanAccess(lead.getOwnerUserId(), lead.getTeamId());
    Long pipelineId = body.pipelineId() != null ? body.pipelineId() : lead.getPipelineId();
    CrmPipelineEntity pipeline = resolvePipeline(tenantId, pipelineId);
    CrmStageEntity stage =
        resolveStage(
            tenantId, pipeline.getId(), body.stageId() != null ? body.stageId() : lead.getStageId());
    apply(lead, body, pipeline.getId(), stage.getId(), false);
    lead.touch();
    return toResponse(leadRepository.save(lead));
  }

  @Transactional
  public LeadResponse patchStatus(Long id, LeadStatusPatch body) {
    String tenantId = TenantIds.require();
    CrmLeadEntity lead = requireLead(tenantId, id);
    recordScopeService.assertCanAccess(lead.getOwnerUserId(), lead.getTeamId());
    Long previousStage = lead.getStageId();
    String previousStatus = lead.getStatus();
    String status = normalizeStatus(body.status());
    lead.setStatus(status);
    if (body.stageId() != null) {
      CrmStageEntity stage = resolveStage(tenantId, lead.getPipelineId(), body.stageId());
      lead.setStageId(stage.getId());
    }
    if (body.lostReasonCode() != null && !body.lostReasonCode().isBlank()) {
      Map<String, Object> attrs =
          lead.getAttributes() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(lead.getAttributes());
      attrs.put("lostReasonCode", body.lostReasonCode().trim());
      lead.setAttributes(attrs);
    }
    lead.touch();
    lead = leadRepository.save(lead);
    timelineService.recordEvent(
        "LEAD",
        lead.getId(),
        "STATUS_CHANGED",
        "Status " + previousStatus + " → " + lead.getStatus()
            + (previousStage.equals(lead.getStageId()) ? "" : (" · stage " + previousStage + " → " + lead.getStageId())),
        Map.of(
            "fromStatus", previousStatus,
            "toStatus", lead.getStatus(),
            "fromStageId", previousStage,
            "toStageId", lead.getStageId()));
    if (body.stageId() != null && !Objects.equals(previousStage, lead.getStageId())) {
      stageAutomationService.onStageChanged(
          "LEAD",
          lead.getId(),
          previousStage,
          lead.getStageId(),
          lead.getOwnerUserId(),
          lead.getTitle() != null ? lead.getTitle() : ("Lead #" + lead.getId()));
    }
    return toResponse(lead);
  }

  @Transactional
  public LeadResponse moveStage(Long id, Long stageId) {
    String tenantId = TenantIds.require();
    CrmLeadEntity lead = requireLead(tenantId, id);
    return patchStatus(id, new LeadStatusPatch(lead.getStatus(), stageId, null));
  }

  @Transactional(readOnly = true)
  public LeadResponse get(Long id) {
    CrmLeadEntity lead = requireLead(TenantIds.require(), id);
    recordScopeService.assertCanAccess(lead.getOwnerUserId(), lead.getTeamId());
    return toResponse(lead);
  }

  @Transactional(readOnly = true)
  public Page<LeadResponse> search(
      String status, Long stageId, Long pipelineId, String ownerUserId, String q, Pageable pageable) {
    String tenantId = TenantIds.require();
    String normalizedStatus = status == null || status.isBlank() ? null : normalizeStatus(status);
    var filter = recordScopeService.listFilter(ownerUserId);
    String scopeMode = filter.scope().name();
    String scopeUserId = filter.scopeUserId();
    List<String> scopeTeamIds =
        filter.scopeTeamIds() == null || filter.scopeTeamIds().isEmpty()
            ? List.of("__NONE__")
            : filter.scopeTeamIds();
    if (filter.scope() == CrmAccessScope.ORG) {
      scopeMode = "ORG";
      scopeUserId = null;
    }
    return leadRepository
        .search(
            tenantId,
            normalizedStatus,
            stageId,
            pipelineId,
            filter.ownerUserId(),
            q,
            scopeMode,
            scopeUserId,
            scopeTeamIds,
            pageable)
        .map(CrmLeadService::toResponse);
  }

  @Transactional
  public void delete(Long id) {
    String tenantId = TenantIds.require();
    CrmLeadEntity lead = requireLead(tenantId, id);
    recordScopeService.assertCanAccess(lead.getOwnerUserId(), lead.getTeamId());
    lead.softDelete();
    leadRepository.save(lead);
  }

  private CrmLeadEntity requireLead(String tenantId, Long id) {
    return leadRepository
        .findByTenantIdAndIdAndDeletedAtIsNull(tenantId, id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead not found"));
  }

  private CrmPipelineEntity resolvePipeline(String tenantId, Long pipelineId) {
    if (pipelineId == null) {
      return workspaceBootstrapService.ensureDefaultPipeline();
    }
    return pipelineRepository
        .findByTenantIdAndIdAndDeletedAtIsNull(tenantId, pipelineId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pipelineId"));
  }

  private CrmStageEntity resolveStage(String tenantId, Long pipelineId, Long stageId) {
    if (stageId == null) {
      return stageRepository
          .findFirstByTenantIdAndPipelineIdAndDeletedAtIsNullOrderBySortOrderAsc(tenantId, pipelineId)
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pipeline has no stages"));
    }
    CrmStageEntity stage =
        stageRepository
            .findByTenantIdAndIdAndDeletedAtIsNull(tenantId, stageId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid stageId"));
    if (!pipelineId.equals(stage.getPipelineId())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "stageId does not belong to pipeline");
    }
    return stage;
  }

  private void apply(
      CrmLeadEntity lead, LeadUpsert body, Long pipelineId, Long stageId, boolean creating) {
    lead.setPipelineId(pipelineId);
    lead.setStageId(stageId);
    lead.setTitle(body.title().trim());
    lead.setDisplayName(blankToNull(body.displayName()));
    lead.setCompanyName(blankToNull(body.companyName()));
    lead.setEmail(blankToNull(body.email()));
    lead.setPhone(blankToNull(body.phone()));
    lead.setSourceCode(blankToNull(body.sourceCode()));
    if (body.status() != null) {
      lead.setStatus(normalizeStatus(body.status()));
    } else if (creating) {
      lead.setStatus("OPEN");
    }
    if (body.priority() != null) {
      lead.setPriority(normalizePriority(body.priority()));
    } else if (creating) {
      lead.setPriority("MEDIUM");
    }
    if (body.score() != null) {
      lead.setScore(body.score());
    }
    if (body.ownerUserId() != null) {
      lead.setOwnerUserId(blankToNull(body.ownerUserId()));
    }
    if (body.teamId() != null) {
      lead.setTeamId(blankToNull(body.teamId()));
    }
    if (body.amount() != null) {
      lead.setAmount(body.amount());
    }
    if (body.currency() != null && !body.currency().isBlank()) {
      lead.setCurrency(body.currency().trim().toUpperCase(Locale.ROOT));
    }
    if (body.attributes() != null) {
      lead.setAttributes(new LinkedHashMap<>(body.attributes()));
    } else if (creating) {
      lead.setAttributes(new LinkedHashMap<>());
    }
    if (body.externalRefs() != null) {
      lead.setExternalRefs(new LinkedHashMap<>(body.externalRefs()));
    } else if (creating) {
      lead.setExternalRefs(new LinkedHashMap<>());
    }
    if (body.formKey() != null) {
      lead.setFormKey(blankToNull(body.formKey()));
    }
    if (body.campaignId() != null) {
      campaignRepository
          .findByTenantIdAndIdAndDeletedAtIsNull(TenantIds.require(), body.campaignId())
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid campaignId"));
      lead.setCampaignId(body.campaignId());
    } else if (creating) {
      lead.setCampaignId(null);
    }
    if (body.utmSource() != null) {
      lead.setUtmSource(blankToNull(body.utmSource()));
    }
    if (body.utmMedium() != null) {
      lead.setUtmMedium(blankToNull(body.utmMedium()));
    }
    if (body.utmCampaign() != null) {
      lead.setUtmCampaign(blankToNull(body.utmCampaign()));
    }
    if (body.utmContent() != null) {
      lead.setUtmContent(blankToNull(body.utmContent()));
    }
    if (body.utmTerm() != null) {
      lead.setUtmTerm(blankToNull(body.utmTerm()));
    }
    if (body.accountId() != null) {
      accountRepository
          .findByTenantIdAndIdAndDeletedAtIsNull(TenantIds.require(), body.accountId())
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid accountId"));
      lead.setAccountId(body.accountId());
    } else if (creating) {
      lead.setAccountId(null);
    }
    if (body.contactId() != null) {
      var contact =
          contactRepository
              .findByTenantIdAndIdAndDeletedAtIsNull(TenantIds.require(), body.contactId())
              .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid contactId"));
      if (body.accountId() != null
          && contact.getAccountId() != null
          && !body.accountId().equals(contact.getAccountId())) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "contactId does not belong to accountId");
      }
      lead.setContactId(body.contactId());
      if (lead.getAccountId() == null && contact.getAccountId() != null) {
        lead.setAccountId(contact.getAccountId());
      }
    } else if (creating) {
      lead.setContactId(null);
    }
  }

  private static String normalizeStatus(String status) {
    String value = status.trim().toUpperCase(Locale.ROOT);
    if (!STATUSES.contains(value)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status: " + status);
    }
    return value;
  }

  private static String normalizePriority(String priority) {
    String value = priority.trim().toUpperCase(Locale.ROOT);
    if (!PRIORITIES.contains(value)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid priority: " + priority);
    }
    return value;
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static LeadResponse toResponse(CrmLeadEntity lead) {
    return new LeadResponse(
        lead.getId(),
        lead.getTenantId(),
        lead.getPipelineId(),
        lead.getStageId(),
        lead.getTitle(),
        lead.getDisplayName(),
        lead.getCompanyName(),
        lead.getEmail(),
        lead.getPhone(),
        lead.getSourceCode(),
        lead.getStatus(),
        lead.getPriority(),
        lead.getScore(),
        lead.getOwnerUserId(),
        lead.getTeamId(),
        lead.getAmount(),
        lead.getCurrency(),
        lead.getAttributes(),
        lead.getExternalRefs(),
        lead.getFormKey(),
        lead.getCampaignId(),
        lead.getUtmSource(),
        lead.getUtmMedium(),
        lead.getUtmCampaign(),
        lead.getUtmContent(),
        lead.getUtmTerm(),
        lead.getAccountId(),
        lead.getContactId(),
        lead.getCreatedAt(),
        lead.getUpdatedAt());
  }
}
