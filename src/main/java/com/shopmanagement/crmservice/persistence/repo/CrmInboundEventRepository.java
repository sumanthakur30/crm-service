package com.shopmanagement.crmservice.persistence.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.crmservice.persistence.entity.CrmInboundEventEntity;

public interface CrmInboundEventRepository extends JpaRepository<CrmInboundEventEntity, Long> {

  Optional<CrmInboundEventEntity> findByProviderAndExternalId(String provider, String externalId);

  List<CrmInboundEventEntity> findByTenantIdOrderByCreatedAtDesc(String tenantId);

  List<CrmInboundEventEntity> findByTenantIdOrderByCreatedAtDesc(String tenantId, Pageable pageable);
}
