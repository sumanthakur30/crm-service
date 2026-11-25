package com.shopmanagement.crmservice.persistence.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.crmservice.persistence.entity.CrmImportBatchEntity;

public interface CrmImportBatchRepository extends JpaRepository<CrmImportBatchEntity, Long> {

  List<CrmImportBatchEntity> findByTenantIdOrderByCreatedAtDesc(String tenantId);
}
