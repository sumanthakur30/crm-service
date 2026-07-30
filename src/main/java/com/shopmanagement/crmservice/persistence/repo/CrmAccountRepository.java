package com.shopmanagement.crmservice.persistence.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.crmservice.persistence.entity.CrmAccountEntity;

public interface CrmAccountRepository extends JpaRepository<CrmAccountEntity, Long> {

  List<CrmAccountEntity> findByTenantIdAndDeletedAtIsNullOrderByNameAsc(String tenantId);

  Optional<CrmAccountEntity> findByTenantIdAndIdAndDeletedAtIsNull(String tenantId, Long id);
}
