package com.shopmanagement.crmservice.persistence.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.crmservice.persistence.entity.CrmAttachmentEntity;

public interface CrmAttachmentRepository extends JpaRepository<CrmAttachmentEntity, Long> {

  List<CrmAttachmentEntity> findByTenantIdAndObjectTypeAndObjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(
      String tenantId, String objectType, Long objectId);

  Optional<CrmAttachmentEntity> findByTenantIdAndIdAndDeletedAtIsNull(String tenantId, Long id);
}
