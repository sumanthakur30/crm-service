package com.shopmanagement.crmservice.persistence.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.crmservice.persistence.entity.CrmTagEntity;

public interface CrmTagRepository extends JpaRepository<CrmTagEntity, Long> {

  List<CrmTagEntity> findByTenantIdAndDeletedAtIsNullOrderByNameAsc(String tenantId);

  Optional<CrmTagEntity> findByTenantIdAndIdAndDeletedAtIsNull(String tenantId, Long id);

  Optional<CrmTagEntity> findByTenantIdAndCodeAndDeletedAtIsNull(String tenantId, String code);
}
