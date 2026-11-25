package com.shopmanagement.crmservice.persistence.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.crmservice.persistence.entity.CrmFieldDefEntity;

public interface CrmFieldDefRepository extends JpaRepository<CrmFieldDefEntity, Long> {

  Optional<CrmFieldDefEntity> findByTenantIdAndObjectTypeAndCodeAndDeletedAtIsNull(
      String tenantId, String objectType, String code);
}
