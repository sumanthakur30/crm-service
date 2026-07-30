package com.shopmanagement.crmservice.persistence.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.crmservice.persistence.entity.CrmContactEntity;

public interface CrmContactRepository extends JpaRepository<CrmContactEntity, Long> {

  List<CrmContactEntity> findByTenantIdAndDeletedAtIsNullOrderByDisplayNameAsc(String tenantId);

  Optional<CrmContactEntity> findByTenantIdAndIdAndDeletedAtIsNull(String tenantId, Long id);

  List<CrmContactEntity> findByTenantIdAndAccountIdAndDeletedAtIsNull(String tenantId, Long accountId);
}
