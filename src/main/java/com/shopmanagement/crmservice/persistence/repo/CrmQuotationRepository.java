package com.shopmanagement.crmservice.persistence.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.crmservice.persistence.entity.CrmQuotationEntity;

public interface CrmQuotationRepository extends JpaRepository<CrmQuotationEntity, Long> {

  Optional<CrmQuotationEntity> findByTenantIdAndIdAndDeletedAtIsNull(String tenantId, Long id);

  List<CrmQuotationEntity> findByTenantIdAndOpportunityIdAndDeletedAtIsNullOrderByVersionNoDesc(
      String tenantId, Long opportunityId);

  List<CrmQuotationEntity> findByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc(String tenantId);

  long countByTenantIdAndDeletedAtIsNull(String tenantId);
}
