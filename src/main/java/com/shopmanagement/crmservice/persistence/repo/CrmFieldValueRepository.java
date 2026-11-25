package com.shopmanagement.crmservice.persistence.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.crmservice.persistence.entity.CrmFieldValueEntity;

public interface CrmFieldValueRepository extends JpaRepository<CrmFieldValueEntity, Long> {

  Optional<CrmFieldValueEntity> findByTenantIdAndObjectTypeAndObjectIdAndFieldDefIdAndDeletedAtIsNull(
      String tenantId, String objectType, Long objectId, Long fieldDefId);
}
