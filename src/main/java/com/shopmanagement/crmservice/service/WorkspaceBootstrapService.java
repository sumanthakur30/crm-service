package com.shopmanagement.crmservice.service;

import java.util.List;
import java.util.Locale;

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
import com.shopmanagement.crmservice.template.IndustryTemplate;
import com.shopmanagement.crmservice.template.IndustryTemplateLoader;

@Service
public class WorkspaceBootstrapService {

  private final CrmWorkspaceRepository workspaceRepository;
  private final CrmPipelineRepository pipelineRepository;
  private final CrmStageRepository stageRepository;
  private final IndustryTemplateLoader templateLoader;

  public WorkspaceBootstrapService(
      CrmWorkspaceRepository workspaceRepository,
      CrmPipelineRepository pipelineRepository,
      CrmStageRepository stageRepository,
      IndustryTemplateLoader templateLoader) {
    this.workspaceRepository = workspaceRepository;
    this.pipelineRepository = pipelineRepository;
    this.stageRepository = stageRepository;
    this.templateLoader = templateLoader;
  }

  @Transactional
  public WorkspaceResponse bootstrap(WorkspaceBootstrapRequest request) {
    String tenantId = TenantIds.require();
    String templateCode =
        request != null && request.templateCode() != null && !request.templateCode().isBlank()
            ? request.templateCode().trim().toUpperCase(Locale.ROOT)
            : "GENERIC";
    IndustryTemplate template = templateLoader.get(templateCode);

    CrmWorkspaceEntity workspace =
        workspaceRepository
            .findByTenantIdAndDeletedAtIsNull(tenantId)
            .orElseGet(() -> createWorkspace(tenantId, request, template));

    if (workspace.getTemplateCode() == null || workspace.getTemplateCode().isBlank()) {
      workspace.setTemplateCode(template.code());
      workspaceRepository.save(workspace);
    }

    CrmPipelineEntity leadPipeline =
        pipelineRepository
            .findFirstByTenantIdAndIsDefaultTrueAndDeletedAtIsNull(tenantId)
            .orElseGet(() -> createPipelineFromSpec(tenantId, template.leadPipeline(), "LEAD", true, 10));

    pipelineRepository
        .findByTenantIdAndCodeAndDeletedAtIsNull(tenantId, template.opportunityPipeline().code())
        .orElseGet(() -> createPipelineFromSpec(tenantId, template.opportunityPipeline(), "OPPORTUNITY", false, 20));

    return toResponse(workspace, leadPipeline.getId());
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

  @Transactional(readOnly = true)
  public List<String> listTemplates() {
    return templateLoader.listCodes();
  }

  @Transactional
  public CrmPipelineEntity ensureDefaultPipeline() {
    String tenantId = TenantIds.require();
    workspaceRepository
        .findByTenantIdAndDeletedAtIsNull(tenantId)
        .orElseGet(() -> createWorkspace(tenantId, new WorkspaceBootstrapRequest(null, "GENERIC"), templateLoader.get("GENERIC")));
    return pipelineRepository
        .findFirstByTenantIdAndIsDefaultTrueAndDeletedAtIsNull(tenantId)
        .orElseGet(
            () ->
                createPipelineFromSpec(
                    tenantId, templateLoader.get("GENERIC").leadPipeline(), "LEAD", true, 10));
  }

  @Transactional
  public CrmPipelineEntity ensureOpportunityPipeline() {
    String tenantId = TenantIds.require();
    CrmWorkspaceEntity ws =
        workspaceRepository
            .findByTenantIdAndDeletedAtIsNull(tenantId)
            .orElseGet(
                () ->
                    createWorkspace(
                        tenantId,
                        new WorkspaceBootstrapRequest(null, "GENERIC"),
                        templateLoader.get("GENERIC")));
    IndustryTemplate template = templateLoader.get(ws.getTemplateCode());
    return pipelineRepository
        .findByTenantIdAndCodeAndDeletedAtIsNull(tenantId, template.opportunityPipeline().code())
        .or(
            () ->
                pipelineRepository.findByTenantIdAndDeletedAtIsNullOrderBySortOrderAsc(tenantId).stream()
                    .filter(p -> "OPPORTUNITY".equalsIgnoreCase(p.getObjectType()))
                    .findFirst())
        .orElseGet(
            () -> createPipelineFromSpec(tenantId, template.opportunityPipeline(), "OPPORTUNITY", false, 20));
  }

  private CrmWorkspaceEntity createWorkspace(
      String tenantId, WorkspaceBootstrapRequest request, IndustryTemplate template) {
    CrmWorkspaceEntity ws = new CrmWorkspaceEntity();
    ws.setTenantId(tenantId);
    String name =
        request != null && request.name() != null && !request.name().isBlank()
            ? request.name().trim()
            : template.name();
    ws.setName(name);
    ws.setTemplateCode(template.code());
    return workspaceRepository.save(ws);
  }

  private CrmPipelineEntity createPipelineFromSpec(
      String tenantId,
      IndustryTemplate.PipelineSpec spec,
      String objectType,
      boolean isDefault,
      int sortOrder) {
    CrmPipelineEntity pipeline = new CrmPipelineEntity();
    pipeline.setTenantId(tenantId);
    pipeline.setCode(spec.code());
    pipeline.setName(spec.name());
    pipeline.setObjectType(objectType);
    pipeline.setDefault(isDefault);
    pipeline.setActive(true);
    pipeline.setSortOrder(sortOrder);
    pipeline = pipelineRepository.save(pipeline);
    for (IndustryTemplate.StageSpec stage : spec.stages()) {
      seedStage(
          tenantId,
          pipeline.getId(),
          stage.code(),
          stage.name(),
          stage.sortOrder(),
          stage.probability(),
          stage.won(),
          stage.lost());
    }
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
