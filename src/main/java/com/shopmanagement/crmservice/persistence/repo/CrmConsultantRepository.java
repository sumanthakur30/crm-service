package com.shopmanagement.crmservice.persistence.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.crmservice.persistence.entity.CrmConsultantEntity;

public interface CrmConsultantRepository extends JpaRepository<CrmConsultantEntity, Long> {

  List<CrmConsultantEntity> findByTenantIdAndDeletedAtIsNullOrderByNameAsc(String tenantId);

  Optional<CrmConsultantEntity> findByTenantIdAndCodeAndDeletedAtIsNull(String tenantId, String code);
}
