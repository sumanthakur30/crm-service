package com.shopmanagement.crmservice.persistence.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.crmservice.persistence.entity.CrmIntegrationSecretEntity;

public interface CrmIntegrationSecretRepository extends JpaRepository<CrmIntegrationSecretEntity, Long> {

  Optional<CrmIntegrationSecretEntity> findByIntegrationIdAndSecretKindAndAssetIdIsNull(
      Long integrationId, String secretKind);

  Optional<CrmIntegrationSecretEntity> findByAssetIdAndSecretKind(Long assetId, String secretKind);

  void deleteByIntegrationIdAndSecretKindAndAssetIdIsNull(Long integrationId, String secretKind);

  void deleteByAssetIdAndSecretKind(Long assetId, String secretKind);
}
