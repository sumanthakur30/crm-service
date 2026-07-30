package com.shopmanagement.crmservice.persistence.repo;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.crmservice.persistence.entity.CrmSequenceEnrollmentEntity;

public interface CrmSequenceEnrollmentRepository extends JpaRepository<CrmSequenceEnrollmentEntity, Long> {

  Optional<CrmSequenceEnrollmentEntity> findByTenantIdAndIdAndDeletedAtIsNull(String tenantId, Long id);

  List<CrmSequenceEnrollmentEntity> findByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc(String tenantId);

  List<CrmSequenceEnrollmentEntity>
      findByTenantIdAndStatusAndNextRunAtLessThanEqualAndDeletedAtIsNullOrderByNextRunAtAsc(
          String tenantId, String status, Instant now);
}
