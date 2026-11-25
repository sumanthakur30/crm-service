package com.shopmanagement.crmservice.persistence.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.crmservice.persistence.entity.CrmPlacementEntity;

public interface CrmPlacementRepository extends JpaRepository<CrmPlacementEntity, Long> {

  List<CrmPlacementEntity> findByTenantIdAndDeletedAtIsNullOrderByIdDesc(String tenantId);

  Optional<CrmPlacementEntity> findByTenantIdAndIdAndDeletedAtIsNull(String tenantId, Long id);
}
