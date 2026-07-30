package com.shopmanagement.crmservice.persistence.repo;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.crmservice.persistence.entity.CrmSequenceEntity;

public interface CrmSequenceRepository extends JpaRepository<CrmSequenceEntity, Long> {

  Optional<CrmSequenceEntity> findByTenantIdAndIdAndDeletedAtIsNull(String tenantId, Long id);

  Optional<CrmSequenceEntity> findByTenantIdAndCodeAndDeletedAtIsNull(String tenantId, String code);

  List<CrmSequenceEntity> findByTenantIdAndDeletedAtIsNullOrderByNameAsc(String tenantId);
}
