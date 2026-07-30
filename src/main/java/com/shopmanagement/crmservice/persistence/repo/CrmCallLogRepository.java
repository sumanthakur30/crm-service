package com.shopmanagement.crmservice.persistence.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.crmservice.persistence.entity.CrmCallLogEntity;

public interface CrmCallLogRepository extends JpaRepository<CrmCallLogEntity, Long> {

  List<CrmCallLogEntity> findByTenantIdAndLeadIdOrderByOccurredAtDesc(String tenantId, Long leadId);
}
