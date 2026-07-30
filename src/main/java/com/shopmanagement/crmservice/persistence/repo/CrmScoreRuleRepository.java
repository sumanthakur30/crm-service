package com.shopmanagement.crmservice.persistence.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.crmservice.persistence.entity.CrmScoreRuleEntity;

public interface CrmScoreRuleRepository extends JpaRepository<CrmScoreRuleEntity, Long> {

  List<CrmScoreRuleEntity> findByTenantIdAndDeletedAtIsNull(String tenantId);

  List<CrmScoreRuleEntity> findByTenantIdAndEventTypeAndActiveTrueAndDeletedAtIsNull(
      String tenantId, String eventType);

  Optional<CrmScoreRuleEntity> findByTenantIdAndCodeAndDeletedAtIsNull(String tenantId, String code);
}
