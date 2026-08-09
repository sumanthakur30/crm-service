package com.shopmanagement.crmservice.persistence.repo;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.crmservice.persistence.entity.CrmTaskEntity;

public interface CrmTaskRepository extends JpaRepository<CrmTaskEntity, Long> {

  Optional<CrmTaskEntity> findByTenantIdAndIdAndDeletedAtIsNull(String tenantId, Long id);

  List<CrmTaskEntity> findByTenantIdAndStatusAndDeletedAtIsNullOrderByDueAtAsc(String tenantId, String status);

  List<CrmTaskEntity> findByTenantIdAndStatusAndDueAtBeforeAndDeletedAtIsNullOrderByDueAtAsc(
      String tenantId, String status, Instant before);

  List<CrmTaskEntity> findByTenantIdAndStatusAndDueAtGreaterThanEqualAndDueAtLessThanAndDeletedAtIsNullOrderByDueAtAsc(
      String tenantId, String status, Instant fromInclusive, Instant toExclusive);

  boolean existsByTenantIdAndRelatedTypeAndRelatedIdAndSlaPolicyIdAndStatusAndDeletedAtIsNull(
      String tenantId, String relatedType, Long relatedId, Long slaPolicyId, String status);

  long countByTenantIdAndStatusAndDueAtBeforeAndDeletedAtIsNull(
      String tenantId, String status, Instant before);
}
