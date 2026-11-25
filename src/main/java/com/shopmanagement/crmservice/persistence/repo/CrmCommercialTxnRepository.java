package com.shopmanagement.crmservice.persistence.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.crmservice.persistence.entity.CrmCommercialTxnEntity;

public interface CrmCommercialTxnRepository extends JpaRepository<CrmCommercialTxnEntity, Long> {

  List<CrmCommercialTxnEntity> findByTenantIdAndPlacementIdAndDeletedAtIsNull(String tenantId, Long placementId);

  List<CrmCommercialTxnEntity> findByTenantIdAndDeletedAtIsNull(String tenantId);
}
