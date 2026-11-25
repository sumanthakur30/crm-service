package com.shopmanagement.crmservice.persistence.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.crmservice.persistence.entity.CrmIntegrationEntity;

public interface CrmIntegrationRepository extends JpaRepository<CrmIntegrationEntity, Long> {

  Optional<CrmIntegrationEntity> findByTenantIdAndProviderAndDeletedAtIsNull(String tenantId, String provider);

  java.util.List<CrmIntegrationEntity> findByProviderAndDeletedAtIsNull(String provider);
}
