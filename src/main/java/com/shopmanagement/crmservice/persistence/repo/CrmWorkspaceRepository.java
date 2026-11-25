package com.shopmanagement.crmservice.persistence.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.shopmanagement.crmservice.persistence.entity.CrmWorkspaceEntity;

public interface CrmWorkspaceRepository extends JpaRepository<CrmWorkspaceEntity, Long> {

  Optional<CrmWorkspaceEntity> findByTenantIdAndDeletedAtIsNull(String tenantId);

  @Query("select w.tenantId from CrmWorkspaceEntity w where w.deletedAt is null")
  List<String> findActiveTenantIds();
}
