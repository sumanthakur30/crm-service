package com.shopmanagement.crmservice.persistence.repo;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shopmanagement.crmservice.persistence.entity.CrmInboundEventEntity;

public interface CrmInboundEventRepository extends JpaRepository<CrmInboundEventEntity, Long> {

  Optional<CrmInboundEventEntity> findByProviderAndExternalId(String provider, String externalId);

  List<CrmInboundEventEntity> findByTenantIdOrderByCreatedAtDesc(String tenantId);

  List<CrmInboundEventEntity> findByTenantIdOrderByCreatedAtDesc(String tenantId, Pageable pageable);

  List<CrmInboundEventEntity> findByTenantIdAndProviderOrderByCreatedAtDesc(
      String tenantId, String provider, Pageable pageable);

  @Query(
      """
      select count(e) from CrmInboundEventEntity e
      where e.tenantId = :tenant and e.provider = :provider and e.status = 'FAILED' and e.createdAt >= :since
      """)
  long countFailedSince(
      @Param("tenant") String tenantId,
      @Param("provider") String provider,
      @Param("since") Instant since);
}
