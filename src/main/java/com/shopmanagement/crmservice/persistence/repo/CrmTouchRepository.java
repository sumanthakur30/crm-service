package com.shopmanagement.crmservice.persistence.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.crmservice.persistence.entity.CrmTouchEntity;

public interface CrmTouchRepository extends JpaRepository<CrmTouchEntity, Long> {

  Optional<CrmTouchEntity> findByTenantIdAndProviderAndExternalId(
      String tenantId, String provider, String externalId);

  boolean existsByTenantIdAndLeadId(String tenantId, Long leadId);
}
