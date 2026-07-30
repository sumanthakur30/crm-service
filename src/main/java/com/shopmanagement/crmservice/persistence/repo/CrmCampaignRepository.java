package com.shopmanagement.crmservice.persistence.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.crmservice.persistence.entity.CrmCampaignEntity;

public interface CrmCampaignRepository extends JpaRepository<CrmCampaignEntity, Long> {

  Optional<CrmCampaignEntity> findByTenantIdAndIdAndDeletedAtIsNull(String tenantId, Long id);

  Optional<CrmCampaignEntity> findByTenantIdAndCodeAndDeletedAtIsNull(String tenantId, String code);

  Optional<CrmCampaignEntity> findByPublicKeyAndDeletedAtIsNull(String publicKey);

  List<CrmCampaignEntity> findByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc(String tenantId);
}
