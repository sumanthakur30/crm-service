package com.shopmanagement.crmservice.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopmanagement.crmservice.persistence.entity.CrmBranchEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmCloseReasonEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmFieldDefEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmInstitutionEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmStageEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmWorkspaceEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmBranchRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmCloseReasonRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmFieldDefRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmInstitutionRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmPipelineRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmStageRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmWorkspaceRepository;

/** Education pack: one campus, course fields, lost reasons, and stage flags. Safe to run again. */
@Service
public class EducationPackService {

  private final CrmWorkspaceRepository workspaceRepository;
  private final CrmInstitutionRepository institutionRepository;
  private final CrmBranchRepository branchRepository;
  private final CrmFieldDefRepository fieldDefRepository;
  private final CrmCloseReasonRepository closeReasonRepository;
  private final CrmPipelineRepository pipelineRepository;
  private final CrmStageRepository stageRepository;

  public EducationPackService(
      CrmWorkspaceRepository workspaceRepository,
      CrmInstitutionRepository institutionRepository,
      CrmBranchRepository branchRepository,
      CrmFieldDefRepository fieldDefRepository,
      CrmCloseReasonRepository closeReasonRepository,
      CrmPipelineRepository pipelineRepository,
      CrmStageRepository stageRepository) {
    this.workspaceRepository = workspaceRepository;
    this.institutionRepository = institutionRepository;
    this.branchRepository = branchRepository;
    this.fieldDefRepository = fieldDefRepository;
    this.closeReasonRepository = closeReasonRepository;
    this.pipelineRepository = pipelineRepository;
    this.stageRepository = stageRepository;
  }

  @Transactional
  public void ensure(String tenantId) {
    CrmWorkspaceEntity workspace =
        workspaceRepository.findByTenantIdAndDeletedAtIsNull(tenantId).orElse(null);
    if (workspace == null || !"EDUCATION".equalsIgnoreCase(workspace.getTemplateCode())) {
      return;
    }
    CrmInstitutionEntity institution =
        institutionRepository
            .findByTenantIdAndCodeAndDeletedAtIsNull(tenantId, "MAIN")
            .orElseGet(
                () -> {
                  CrmInstitutionEntity row = new CrmInstitutionEntity();
                  row.setTenantId(tenantId);
                  row.setCode("MAIN");
                  row.setName(workspace.getName() == null ? "School" : workspace.getName());
                  row.setActive(true);
                  return institutionRepository.save(row);
                });
    branchRepository
        .findByTenantIdAndCodeAndDeletedAtIsNull(tenantId, "MAIN")
        .orElseGet(
            () -> {
              CrmBranchEntity branch = new CrmBranchEntity();
              branch.setTenantId(tenantId);
              branch.setInstitutionId(institution.getId());
              branch.setCode("MAIN");
              branch.setName("Main campus");
              branch.setTimezone("Asia/Kolkata");
              branch.setActive(true);
              return branchRepository.save(branch);
            });
    seedFields(tenantId);
    seedReasons(tenantId);
    pipelineRepository
        .findFirstByTenantIdAndIsDefaultTrueAndDeletedAtIsNull(tenantId)
        .ifPresent(pipeline -> flagStages(tenantId, pipeline.getId()));
  }

  private void seedFields(String tenantId) {
    seedField(tenantId, "course", "Course", "TEXT", 10, List.of());
    seedField(tenantId, "program", "Program", "TEXT", 20, List.of());
    seedField(tenantId, "class_grade", "Class", "TEXT", 30, List.of());
    seedField(tenantId, "academic_session", "Session", "TEXT", 40, List.of());
    seedField(tenantId, "admission_year", "Admission year", "TEXT", 50, List.of());
    seedField(tenantId, "study_preference", "Study preference", "SINGLE_SELECT", 60, List.of("Day", "Hostel", "Online"));
  }

  private void seedField(
      String tenantId, String code, String label, String type, int sort, List<String> options) {
    if (fieldDefRepository
        .findByTenantIdAndObjectTypeAndCodeAndDeletedAtIsNull(tenantId, "LEAD", code)
        .isPresent()) {
      return;
    }
    CrmFieldDefEntity def = new CrmFieldDefEntity();
    def.setTenantId(tenantId);
    def.setObjectType("LEAD");
    def.setCode(code);
    def.setLabel(label);
    def.setDataType(type);
    def.setSortOrder(sort);
    def.setOptions(options);
    fieldDefRepository.save(def);
  }

  private void seedReasons(String tenantId) {
    seedReason(tenantId, "FEE", "Fees", 10);
    seedReason(tenantId, "DISTANCE", "Distance", 20);
    seedReason(tenantId, "NOT_INTERESTED", "Not interested", 30);
    seedReason(tenantId, "JOINED_ELSEWHERE", "Joined another school", 40);
    seedReason(tenantId, "NO_RESPONSE", "No response", 50);
  }

  private void seedReason(String tenantId, String code, String name, int sort) {
    if (closeReasonRepository.findByTenantIdAndCodeAndDeletedAtIsNull(tenantId, code).isPresent()) {
      return;
    }
    CrmCloseReasonEntity reason = new CrmCloseReasonEntity();
    reason.setTenantId(tenantId);
    reason.setCode(code);
    reason.setName(name);
    reason.setOutcome("LOST");
    reason.setSortOrder(sort);
    reason.setActive(true);
    closeReasonRepository.save(reason);
  }

  private void flagStages(String tenantId, Long pipelineId) {
    for (CrmStageEntity stage :
        stageRepository.findByTenantIdAndPipelineIdAndDeletedAtIsNullOrderBySortOrderAsc(tenantId, pipelineId)) {
      switch (stage.getCode()) {
        case "COUNSELING" -> stage.setContacted(true);
        case "APPLICATION" -> {
          stage.setContacted(true);
          stage.setApplication(true);
        }
        case "FEE_QUOTE" -> {
          stage.setContacted(true);
          stage.setApplication(true);
          stage.setQualified(true);
        }
        case "ENROLLED" -> {
          stage.setContacted(true);
          stage.setApplication(true);
          stage.setQualified(true);
        }
        default -> {
          continue;
        }
      }
      stageRepository.save(stage);
    }
  }
}
