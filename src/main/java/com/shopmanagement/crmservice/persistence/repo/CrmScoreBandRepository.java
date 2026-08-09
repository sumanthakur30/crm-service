package com.shopmanagement.crmservice.persistence.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.crmservice.persistence.entity.CrmScoreBandEntity;

public interface CrmScoreBandRepository extends JpaRepository<CrmScoreBandEntity, Long> {

  Optional<CrmScoreBandEntity> findByTenantIdAndDeletedAtIsNull(String tenantId);
}
