package com.shopmanagement.crmservice.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.shopmanagement.crmservice.api.CrmLeadApi.PipelineResponse;
import com.shopmanagement.crmservice.api.CrmLeadApi.StageResponse;
import com.shopmanagement.crmservice.api.CrmLeadApi.WorkspaceBootstrapRequest;
import com.shopmanagement.crmservice.api.CrmLeadApi.WorkspaceResponse;
import com.shopmanagement.crmservice.persistence.entity.CrmPipelineEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmStageEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmWorkspaceEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmPipelineRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmStageRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmWorkspaceRepository;
import com.shopmanagement.crmservice.support.TenantIds;

@Service
public class WorkspaceBootstrapService {

  private final CrmWorkspaceRepository workspaceRepository;
  private final CrmPipelineRepository pipelineRepository;
  private final CrmStageRepository stageRepository;

  public WorkspaceBootstrapService(
      CrmWorkspaceRepository workspaceRepository,
      CrmPipelineRepository pipelineRepository,
      CrmStageRepository stageRepository) {
    this.workspaceRepository = workspaceRepository;
    this.pipelineRepository = pipelineRepository;
    this.stageRepository = stageRepository;
  }

  @Transactional
  public WorkspaceResponse bootstrap(WorkspaceBootstrapRequest request) {
    String tenantId = TenantIds.require();
    CrmWorkspaceEntity workspace =
        workspaceRepository
            .findByTenantIdAndDeletedAtIsNull(tenantId)
            .orElseGet(() -> createWorkspace(tenantId, request));

    CrmPipelineEntity pipeline =
        pipelineRepository
            .findFirstByTenantIdAndIsDefaultTrueAndDeletedAtIsNull(tenantId)
            .orElseGet(() -> createDefaultPipeline(tenantId));

    return toResponse(workspace, pipeline.getId());
  }

  @Transactional(readOnly = true)
  public WorkspaceResponse current() {
    String tenantId = TenantIds.require();
    CrmWorkspaceEntity workspace =
        workspaceRepository
            .findByTenantIdAndDeletedAtIsNull(tenantId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not bootstrapped"));
    Long pipelineId =
        pipelineRepository
            .findFirstByTenantIdAndIsDefaultTrueAndDeletedAtIsNull(tenantId)
            .map(CrmPipelineEntity::getId)
            .orElse(null);
    return toResponse(workspace, pipelineId);
  }

  @Transactional(readOnly = true)
  public List<PipelineResponse> listPipelines() {
    String tenantId = TenantIds.require();
    return pipelineRepository.findByTenantIdAndDeletedAtIsNullOrderBySortOrderAsc(tenantId).stream()
        .map(
            p ->
                new PipelineResponse(
                    p.getId(), p.getCode(), p.getName(), p.getObjectType(), p.isDefault()))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<StageResponse> listStages(Long pipelineId) {
    String tenantId = TenantIds.require();
    pipelineRepository
        .findByTenantIdAndIdAndDeletedAtIsNull(tenantId, pipelineId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pipeline not found"));
    return stageRepository
        .findByTenantIdAndPipelineIdAndDeletedAtIsNullOrderBySortOrderAsc(tenantId, pipelineId)
        .stream()
        .map(
            s ->
                new StageResponse(
                    s.getId(),
                    s.getPipelineId(),
                    s.getCode(),
                    s.getName(),
                    s.getSortOrder(),
                    s.getProbability(),
                    s.isWon(),
                    s.isLost()))
        .toList();
  }

  /** Ensures workspace + default pipeline exist; returns default pipeline id. */
  @Transactional
  public CrmPipelineEntity ensureDefaultPipeline() {
    String tenantId = TenantIds.require();
    workspaceRepository
        .findByTenantIdAndDeletedAtIsNull(tenantId)
        .orElseGet(() -> createWorkspace(tenantId, new WorkspaceBootstrapRequest(null, "GENERIC")));
    return pipelineRepository
        .findFirstByTenantIdAndIsDefaultTrueAndDeletedAtIsNull(tenantId)
        .orElseGet(() -> createDefaultPipeline(tenantId));
  }

  private CrmWorkspaceEntity createWorkspace(String tenantId, WorkspaceBootstrapRequest request) {
    CrmWorkspaceEntity ws = new CrmWorkspaceEntity();
    ws.setTenantId(tenantId);
    String name =
        request != null && request.name() != null && !request.name().isBlank()
            ? request.name().trim()
            : "CRM Workspace";
    ws.setName(name);
    String template =
        request != null && request.templateCode() != null && !request.templateCode().isBlank()
            ? request.templateCode().trim().toUpperCase()
            : "GENERIC";
    ws.setTemplateCode(template);
    return workspaceRepository.save(ws);
  }

  private CrmPipelineEntity createDefaultPipeline(String tenantId) {
    CrmPipelineEntity pipeline = new CrmPipelineEntity();
    pipeline.setTenantId(tenantId);
    pipeline.setCode("SALES");
    pipeline.setName("Sales Pipeline");
    pipeline.setObjectType("LEAD");
    pipeline.setDefault(true);
    pipeline.setActive(true);
    pipeline.setSortOrder(10);
    pipeline = pipelineRepository.save(pipeline);

    seedStage(tenantId, pipeline.getId(), "NEW", "New", 10, 10, false, false);
    seedStage(tenantId, pipeline.getId(), "CONTACTED", "Contacted", 20, 25, false, false);
    seedStage(tenantId, pipeline.getId(), "QUALIFIED", "Qualified", 30, 50, false, false);
    seedStage(tenantId, pipeline.getId(), "PROPOSAL", "Proposal", 40, 75, false, false);
    seedStage(tenantId, pipeline.getId(), "WON", "Won", 50, 100, true, false);
    seedStage(tenantId, pipeline.getId(), "LOST", "Lost", 60, 0, false, true);
    return pipeline;
  }

  private void seedStage(
      String tenantId,
      Long pipelineId,
      String code,
      String name,
      int sort,
      int probability,
      boolean won,
      boolean lost) {
    CrmStageEntity stage = new CrmStageEntity();
    stage.setTenantId(tenantId);
    stage.setPipelineId(pipelineId);
    stage.setCode(code);
    stage.setName(name);
    stage.setSortOrder(sort);
    stage.setProbability(probability);
    stage.setWon(won);
    stage.setLost(lost);
    stage.setActive(true);
    stageRepository.save(stage);
  }

  private static WorkspaceResponse toResponse(CrmWorkspaceEntity ws, Long pipelineId) {
    return new WorkspaceResponse(
        ws.getId(),
        ws.getTenantId(),
        ws.getName(),
        ws.getTemplateCode(),
        ws.getTimezone(),
        ws.getCurrency(),
        pipelineId);
  }
}
