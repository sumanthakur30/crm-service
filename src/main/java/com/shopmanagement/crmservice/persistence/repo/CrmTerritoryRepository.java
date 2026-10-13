package com.shopmanagement.crmservice.persistence.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.crmservice.persistence.entity.CrmTerritoryEntity;

public interface CrmTerritoryRepository extends JpaRepository<CrmTerritoryEntity, Long> {
  List<CrmTerritoryEntity> findByTenantIdAndDeletedAtIsNullOrderByCodeAsc(String tenantId);

  Optional<CrmTerritoryEntity> findByTenantIdAndIdAndDeletedAtIsNull(String tenantId, Long id);

  Optional<CrmTerritoryEntity> findByTenantIdAndCodeAndDeletedAtIsNull(String tenantId, String code);
}
