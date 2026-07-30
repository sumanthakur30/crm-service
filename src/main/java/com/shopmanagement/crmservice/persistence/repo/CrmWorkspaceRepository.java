package com.shopmanagement.crmservice.persistence.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.crmservice.persistence.entity.CrmWorkspaceEntity;

public interface CrmWorkspaceRepository extends JpaRepository<CrmWorkspaceEntity, Long> {

  Optional<CrmWorkspaceEntity> findByTenantIdAndDeletedAtIsNull(String tenantId);
}
