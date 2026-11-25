package com.shopmanagement.crmservice.persistence.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.crmservice.persistence.entity.CrmCommercialAgreementEntity;

public interface CrmCommercialAgreementRepository extends JpaRepository<CrmCommercialAgreementEntity, Long> {

  List<CrmCommercialAgreementEntity> findByTenantIdAndDeletedAtIsNullOrderByIdDesc(String tenantId);
}
