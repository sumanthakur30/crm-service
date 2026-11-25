package com.shopmanagement.crmservice.persistence.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.crmservice.persistence.entity.CrmLeadContactEntity;

public interface CrmLeadContactRepository extends JpaRepository<CrmLeadContactEntity, Long> {

  Optional<CrmLeadContactEntity> findByTenantIdAndLeadIdAndContactIdAndRoleAndDeletedAtIsNull(
      String tenantId, Long leadId, Long contactId, String role);
}
