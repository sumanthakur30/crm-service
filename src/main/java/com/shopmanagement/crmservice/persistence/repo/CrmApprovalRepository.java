package com.shopmanagement.crmservice.persistence.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.crmservice.persistence.entity.CrmApprovalEntity;

public interface CrmApprovalRepository extends JpaRepository<CrmApprovalEntity, Long> {

  Optional<CrmApprovalEntity> findByTenantIdAndId(String tenantId, Long id);

  List<CrmApprovalEntity> findByTenantIdAndStatusOrderByCreatedAtDesc(String tenantId, String status);

  Optional<CrmApprovalEntity> findFirstByTenantIdAndObjectTypeAndObjectIdAndStatusOrderByCreatedAtDesc(
      String tenantId, String objectType, Long objectId, String status);
}
