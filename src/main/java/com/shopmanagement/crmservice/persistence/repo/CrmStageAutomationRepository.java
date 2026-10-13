package com.shopmanagement.crmservice.persistence.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.crmservice.persistence.entity.CrmStageAutomationEntity;

public interface CrmStageAutomationRepository extends JpaRepository<CrmStageAutomationEntity, Long> {

  List<CrmStageAutomationEntity> findByTenantIdAndObjectTypeAndActiveTrueAndDeletedAtIsNullOrderBySortOrderAscIdAsc(
      String tenantId, String objectType);

  List<CrmStageAutomationEntity> findByTenantIdAndDeletedAtIsNullOrderByObjectTypeAscSortOrderAscIdAsc(
      String tenantId);

  java.util.Optional<CrmStageAutomationEntity> findByTenantIdAndIdAndDeletedAtIsNull(
      String tenantId, Long id);

  long countByTenantIdAndDeletedAtIsNull(String tenantId);
}
