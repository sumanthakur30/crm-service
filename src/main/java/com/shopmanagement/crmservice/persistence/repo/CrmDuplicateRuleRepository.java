package com.shopmanagement.crmservice.persistence.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.crmservice.persistence.entity.CrmDuplicateRuleEntity;

public interface CrmDuplicateRuleRepository extends JpaRepository<CrmDuplicateRuleEntity, Long> {

  List<CrmDuplicateRuleEntity> findByTenantIdAndObjectTypeAndDeletedAtIsNullOrderByWeightDescIdAsc(
      String tenantId, String objectType);

  Optional<CrmDuplicateRuleEntity> findByTenantIdAndIdAndDeletedAtIsNull(String tenantId, Long id);

  Optional<CrmDuplicateRuleEntity> findByTenantIdAndCodeAndDeletedAtIsNull(String tenantId, String code);

  long countByTenantIdAndObjectTypeAndDeletedAtIsNull(String tenantId, String objectType);
}
