package com.shopmanagement.crmservice.persistence.repo;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.crmservice.persistence.entity.CrmNoteEntity;

public interface CrmNoteRepository extends JpaRepository<CrmNoteEntity, Long> {

  List<CrmNoteEntity> findByTenantIdAndRelatedTypeAndRelatedIdAndDeletedAtIsNullOrderByCreatedAtDesc(
      String tenantId, String relatedType, Long relatedId);

  List<CrmNoteEntity> findByTenantIdAndCreatedAtGreaterThanEqualAndDeletedAtIsNullOrderByCreatedAtDesc(
      String tenantId, Instant from);

  Optional<CrmNoteEntity> findByTenantIdAndIdAndDeletedAtIsNull(String tenantId, Long id);
}
