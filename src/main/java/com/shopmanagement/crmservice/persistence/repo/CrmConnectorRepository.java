package com.shopmanagement.crmservice.persistence.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.crmservice.persistence.entity.CrmConnectorEntity;

public interface CrmConnectorRepository extends JpaRepository<CrmConnectorEntity, Long> {
  List<CrmConnectorEntity> findByTenantIdAndDeletedAtIsNullOrderByProviderAsc(String tenantId);

  Optional<CrmConnectorEntity> findByTenantIdAndProviderAndDeletedAtIsNull(String tenantId, String provider);
}
