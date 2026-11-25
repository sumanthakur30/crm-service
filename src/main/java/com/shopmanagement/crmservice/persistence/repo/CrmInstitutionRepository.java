package com.shopmanagement.crmservice.persistence.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.crmservice.persistence.entity.CrmInstitutionEntity;

public interface CrmInstitutionRepository extends JpaRepository<CrmInstitutionEntity, Long> {

  Optional<CrmInstitutionEntity> findByTenantIdAndCodeAndDeletedAtIsNull(String tenantId, String code);

  List<CrmInstitutionEntity> findByTenantIdAndDeletedAtIsNullOrderByNameAsc(String tenantId);
}
