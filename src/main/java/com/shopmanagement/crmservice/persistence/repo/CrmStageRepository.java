package com.shopmanagement.crmservice.persistence.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.crmservice.persistence.entity.CrmStageEntity;

public interface CrmStageRepository extends JpaRepository<CrmStageEntity, Long> {

  List<CrmStageEntity> findByTenantIdAndPipelineIdAndDeletedAtIsNullOrderBySortOrderAsc(
      String tenantId, Long pipelineId);

  Optional<CrmStageEntity> findByTenantIdAndIdAndDeletedAtIsNull(String tenantId, Long id);

  Optional<CrmStageEntity> findFirstByTenantIdAndPipelineIdAndDeletedAtIsNullOrderBySortOrderAsc(
      String tenantId, Long pipelineId);
}
