package com.shopmanagement.crmservice.persistence.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.crmservice.persistence.entity.CrmBranchEntity;

public interface CrmBranchRepository extends JpaRepository<CrmBranchEntity, Long> {

  Optional<CrmBranchEntity> findByTenantIdAndCodeAndDeletedAtIsNull(String tenantId, String code);
}
