package com.shopmanagement.crmservice.persistence.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.crmservice.persistence.entity.CrmAuditExportJobEntity;

public interface CrmAuditExportJobRepository extends JpaRepository<CrmAuditExportJobEntity, Long> {

  List<CrmAuditExportJobEntity> findByTenantIdOrderByCreatedAtDesc(String tenantId);
}
