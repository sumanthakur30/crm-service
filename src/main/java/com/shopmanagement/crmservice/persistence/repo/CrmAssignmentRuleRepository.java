package com.shopmanagement.crmservice.persistence.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.crmservice.persistence.entity.CrmAssignmentRuleEntity;

public interface CrmAssignmentRuleRepository extends JpaRepository<CrmAssignmentRuleEntity, Long> {

  List<CrmAssignmentRuleEntity> findByTenantIdAndActiveTrueOrderBySortOrderAscIdAsc(String tenantId);

  List<CrmAssignmentRuleEntity> findByTenantIdOrderBySortOrderAscIdAsc(String tenantId);
}
