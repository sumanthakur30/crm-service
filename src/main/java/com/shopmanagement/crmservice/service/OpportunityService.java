package com.shopmanagement.crmservice.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.shopmanagement.crmservice.api.CrmDealApi.OpportunityResponse;
import com.shopmanagement.crmservice.api.CrmDealApi.OpportunityUpsert;
import com.shopmanagement.crmservice.persistence.entity.CrmOpportunityEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmPipelineEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmStageEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmLeadRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmOpportunityRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmPipelineRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmStageRepository;
import com.shopmanagement.crmservice.support.TenantIds;

@Service
public class OpportunityService {

  private static final Set<String> STATUSES = Set.of("OPEN", "WON", "LOST", "ABANDONED");

  private final CrmOpportunityRepository opportunityRepository;
  private final CrmPipelineRepository pipelineRepository;
  private final CrmStageRepository stageRepository;
  private final CrmLeadRepository leadRepository;
  private final WorkspaceBootstrapService workspaceBootstrapService;
  private final TimelineService timelineService;

  public OpportunityService(
      CrmOpportunityRepository opportunityRepository,
      CrmPipelineRepository pipelineRepository,
      CrmStageRepository stageRepository,
      CrmLeadRepository leadRepository,
      WorkspaceBootstrapService workspaceBootstrapService,
      TimelineService timelineService) {
    this.opportunityRepository = opportunityRepository;
    this.pipelineRepository = pipelineRepository;
    this.stageRepository = stageRepository;
    this.leadRepository = leadRepository;
    this.workspaceBootstrapService = workspaceBootstrapService;
    this.timelineService = timelineService;
  }

  @Transactional
  public OpportunityResponse create(OpportunityUpsert body) {
    String tenantId = TenantIds.require();
    CrmPipelineEntity pipeline = resolveOppPipeline(tenantId, body.pipelineId());
    CrmStageEntity stage = resolveStage(tenantId, pipeline.getId(), body.stageId());
    if (body.leadId() != null) {
      leadRepository
          .findByTenantIdAndIdAndDeletedAtIsNull(tenantId, body.leadId())
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid leadId"));
    }

    CrmOpportunityEntity opp = new CrmOpportunityEntity();
    opp.setTenantId(tenantId);
    apply(opp, body, pipeline.getId(), stage);
    if (opp.getOwnerUserId() == null) {
      opp.setOwnerUserId(TenantIds.currentUserOrNull());
    }
    opp = opportunityRepository.save(opp);
    timelineService.recordEvent(
        "OPPORTUNITY",
        opp.getId(),
        "OPPORTUNITY_CREATED",
        "Opportunity created: " + opp.getName(),
        Map.of("stageId", opp.getStageId()));
    return toResponse(opp);
  }

  @Transactional
  public OpportunityResponse update(Long id, OpportunityUpsert body) {
    String tenantId = TenantIds.require();
    CrmOpportunityEntity opp = require(tenantId, id);
    CrmPipelineEntity pipeline =
        resolveOppPipeline(tenantId, body.pipelineId() != null ? body.pipelineId() : opp.getPipelineId());
    CrmStageEntity stage =
        resolveStage(
            tenantId, pipeline.getId(), body.stageId() != null ? body.stageId() : opp.getStageId());
    apply(opp, body, pipeline.getId(), stage);
    opp.touch();
    return toResponse(opportunityRepository.save(opp));
  }

  @Transactional
  public OpportunityResponse moveStage(Long id, Long stageId) {
    String tenantId = TenantIds.require();
    CrmOpportunityEntity opp = require(tenantId, id);
    Long from = opp.getStageId();
    CrmStageEntity stage = resolveStage(tenantId, opp.getPipelineId(), stageId);
    opp.setStageId(stage.getId());
    opp.setProbability(stage.getProbability());
    if (stage.isWon()) {
      opp.setStatus("WON");
    } else if (stage.isLost()) {
      opp.setStatus("LOST");
    }
    opp.touch();
    opp = opportunityRepository.save(opp);
    timelineService.recordEvent(
        "OPPORTUNITY",
        id,
        "STAGE_CHANGED",
        "Stage " + from + " → " + stageId,
        Map.of("fromStageId", from, "toStageId", stageId));
    return toResponse(opp);
  }

  @Transactional(readOnly = true)
  public OpportunityResponse get(Long id) {
    return toResponse(require(TenantIds.require(), id));
  }

  @Transactional(readOnly = true)
  public Page<OpportunityResponse> search(String status, Long stageId, String q, Pageable pageable) {
    String tenantId = TenantIds.require();
    String st = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
    return opportunityRepository.search(tenantId, st, stageId, q, pageable).map(OpportunityService::toResponse);
  }

  private CrmOpportunityEntity require(String tenantId, Long id) {
    return opportunityRepository
        .findByTenantIdAndIdAndDeletedAtIsNull(tenantId, id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Opportunity not found"));
  }

  private CrmPipelineEntity resolveOppPipeline(String tenantId, Long pipelineId) {
    if (pipelineId == null) {
      return workspaceBootstrapService.ensureOpportunityPipeline();
    }
    CrmPipelineEntity pipeline =
        pipelineRepository
            .findByTenantIdAndIdAndDeletedAtIsNull(tenantId, pipelineId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pipelineId"));
    if (!"OPPORTUNITY".equalsIgnoreCase(pipeline.getObjectType())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "pipelineId must be an OPPORTUNITY pipeline");
    }
    return pipeline;
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
      CrmOpportunityEntity opp, OpportunityUpsert body, Long pipelineId, CrmStageEntity stage) {
    opp.setPipelineId(pipelineId);
    opp.setStageId(stage.getId());
    opp.setName(body.name().trim());
    if (body.leadId() != null) {
      opp.setLeadId(body.leadId());
    }
    if (body.amount() != null) {
      opp.setAmount(body.amount());
    }
    if (body.currency() != null && !body.currency().isBlank()) {
      opp.setCurrency(body.currency().trim().toUpperCase(Locale.ROOT));
    }
    opp.setProbability(body.probability() != null ? body.probability() : stage.getProbability());
    if (body.expectedCloseDate() != null) {
      opp.setExpectedCloseDate(body.expectedCloseDate());
    }
    if (body.status() != null) {
      String st = body.status().trim().toUpperCase(Locale.ROOT);
      if (!STATUSES.contains(st)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status");
      }
      opp.setStatus(st);
    }
    if (body.ownerUserId() != null) {
      opp.setOwnerUserId(blankToNull(body.ownerUserId()));
    }
    if (body.teamId() != null) {
      opp.setTeamId(blankToNull(body.teamId()));
    }
    if (body.attributes() != null) {
      opp.setAttributes(new LinkedHashMap<>(body.attributes()));
    }
  }

  private static String blankToNull(String v) {
    return v == null || v.isBlank() ? null : v.trim();
  }

  private static OpportunityResponse toResponse(CrmOpportunityEntity o) {
    return new OpportunityResponse(
        o.getId(),
        o.getTenantId(),
        o.getPipelineId(),
        o.getStageId(),
        o.getLeadId(),
        o.getName(),
        o.getAmount(),
        o.getCurrency(),
        o.getProbability(),
        o.getExpectedCloseDate(),
        o.getStatus(),
        o.getOwnerUserId(),
        o.getTeamId(),
        o.getAttributes(),
        o.getCreatedAt(),
        o.getUpdatedAt());
  }
}
