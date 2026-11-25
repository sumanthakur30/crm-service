package com.shopmanagement.crmservice.integration.meta;

import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopmanagement.crmservice.config.CrmMetaProperties;
import com.shopmanagement.crmservice.integration.IntegrationCipher;
import com.shopmanagement.crmservice.persistence.entity.CrmIntegrationAssetEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmIntegrationEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmIntegrationSecretEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmIntegrationAssetRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmIntegrationRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmIntegrationSecretRepository;

/** One Graph check per integration per day. A dead token waits; it does not create a lead. */
@Service
public class MetaTokenCheckService {

  private static final Duration INTERVAL = Duration.ofHours(24);

  private final CrmMetaProperties properties;
  private final MetaGraphClient graph;
  private final IntegrationCipher cipher;
  private final CrmIntegrationRepository integrationRepository;
  private final CrmIntegrationAssetRepository assetRepository;
  private final CrmIntegrationSecretRepository secretRepository;

  public MetaTokenCheckService(
      CrmMetaProperties properties,
      MetaGraphClient graph,
      IntegrationCipher cipher,
      CrmIntegrationRepository integrationRepository,
      CrmIntegrationAssetRepository assetRepository,
      CrmIntegrationSecretRepository secretRepository) {
    this.properties = properties;
    this.graph = graph;
    this.cipher = cipher;
    this.integrationRepository = integrationRepository;
    this.assetRepository = assetRepository;
    this.secretRepository = secretRepository;
  }

  @Transactional
  public void checkDue() {
    if (!properties.isConfigured()) {
      return;
    }
    Instant cutoff = Instant.now().minus(INTERVAL);
    for (CrmIntegrationEntity integration : integrationRepository.findByProviderAndDeletedAtIsNull("META")) {
      if ("DISCONNECTED".equals(integration.getStatus())) {
        continue;
      }
      if (integration.getLastTokenCheckAt() != null && integration.getLastTokenCheckAt().isAfter(cutoff)) {
        continue;
      }
      String token;
      try {
        token = tokenFor(integration);
      } catch (RuntimeException ex) {
        integration.setStatus("ERROR");
        integration.setLastError("Reconnect Facebook. The page token is no longer valid.");
        integration.setLastTokenCheckAt(Instant.now());
        integration.touch();
        integrationRepository.save(integration);
        continue;
      }
      integration.setLastTokenCheckAt(Instant.now());
      if (token == null) {
        integration.touch();
        integrationRepository.save(integration);
        continue;
      }
      boolean works;
      try {
        works = graph.tokenWorks(token);
      } catch (RuntimeException ex) {
        works = false;
      }
      if (!works) {
        integration.setStatus("ERROR");
        integration.setLastError("Reconnect Facebook. The page token is no longer valid.");
      }
      integration.touch();
      integrationRepository.save(integration);
    }
  }

  private String tokenFor(CrmIntegrationEntity integration) {
    CrmIntegrationSecretEntity user =
        secretRepository
            .findByIntegrationIdAndSecretKindAndAssetIdIsNull(integration.getId(), "USER_TOKEN")
            .orElse(null);
    if (user != null) {
      return cipher.decrypt(user.getCiphertext());
    }
    for (CrmIntegrationAssetEntity page :
        assetRepository.findByIntegrationIdAndAssetTypeAndSelectedTrue(integration.getId(), "PAGE")) {
      CrmIntegrationSecretEntity secret =
          secretRepository.findByAssetIdAndSecretKind(page.getId(), "PAGE_TOKEN").orElse(null);
      if (secret != null) {
        return cipher.decrypt(secret.getCiphertext());
      }
    }
    return null;
  }
}
