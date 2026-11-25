package com.shopmanagement.crmservice.persistence.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Ciphertext only. Do not add a plaintext column or put this entity on an API response. */
@Entity
@Table(name = "crm_integration_secret")
@Getter
@Setter
public class CrmIntegrationSecretEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "integration_id", nullable = false)
  private Long integrationId;

  @Column(name = "asset_id")
  private Long assetId;

  @Column(name = "secret_kind", nullable = false, length = 32)
  private String secretKind;

  @Column(nullable = false, columnDefinition = "text")
  private String ciphertext;

  @Column(name = "expires_at")
  private Instant expiresAt;

  @Column(name = "rotated_at", nullable = false)
  private Instant rotatedAt = Instant.now();
}
