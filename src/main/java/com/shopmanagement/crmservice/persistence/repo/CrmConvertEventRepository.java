package com.shopmanagement.crmservice.persistence.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.crmservice.persistence.entity.CrmConvertEventEntity;

public interface CrmConvertEventRepository extends JpaRepository<CrmConvertEventEntity, Long> {

  List<CrmConvertEventEntity> findByTenantIdAndLeadIdOrderByCreatedAtDesc(String tenantId, Long leadId);

  List<CrmConvertEventEntity> findByTenantIdOrderByCreatedAtDesc(String tenantId);
}
