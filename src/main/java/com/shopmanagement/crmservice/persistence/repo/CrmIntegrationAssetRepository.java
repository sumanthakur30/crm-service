package com.shopmanagement.crmservice.persistence.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.crmservice.persistence.entity.CrmIntegrationAssetEntity;

public interface CrmIntegrationAssetRepository extends JpaRepository<CrmIntegrationAssetEntity, Long> {

  List<CrmIntegrationAssetEntity> findByIntegrationIdOrderByAssetTypeAscNameAsc(Long integrationId);

  Optional<CrmIntegrationAssetEntity> findByAssetTypeAndExternalIdAndSelectedTrue(String assetType, String externalId);

  List<CrmIntegrationAssetEntity> findByAssetTypeAndExternalId(String assetType, String externalId);

  Optional<CrmIntegrationAssetEntity> findByIntegrationIdAndAssetTypeAndExternalId(
      Long integrationId, String assetType, String externalId);

  List<CrmIntegrationAssetEntity> findByIntegrationIdAndAssetTypeAndSelectedTrue(Long integrationId, String assetType);
}
