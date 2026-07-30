package com.shopmanagement.crmservice.persistence.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.crmservice.persistence.entity.CrmPipelineEntity;

public interface CrmPipelineRepository extends JpaRepository<CrmPipelineEntity, Long> {

  List<CrmPipelineEntity> findByTenantIdAndDeletedAtIsNullOrderBySortOrderAsc(String tenantId);

  Optional<CrmPipelineEntity> findByTenantIdAndIdAndDeletedAtIsNull(String tenantId, Long id);

  Optional<CrmPipelineEntity> findFirstByTenantIdAndIsDefaultTrueAndDeletedAtIsNull(String tenantId);

  Optional<CrmPipelineEntity> findByTenantIdAndCodeAndDeletedAtIsNull(String tenantId, String code);
}
