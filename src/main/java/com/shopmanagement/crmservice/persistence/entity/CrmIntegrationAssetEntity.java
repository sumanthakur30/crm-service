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

@Entity
@Table(name = "crm_integration_asset")
@Getter
@Setter
public class CrmIntegrationAssetEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id", nullable = false, length = 64)
  private String tenantId;

  @Column(name = "integration_id", nullable = false)
  private Long integrationId;

  @Column(name = "asset_type", nullable = false, length = 32)
  private String assetType;

  @Column(name = "external_id", nullable = false, length = 128)
  private String externalId;

  @Column(length = 256)
  private String name;

  @Column(nullable = false)
  private boolean selected;

  @Column(name = "branch_id")
  private Long branchId;

  @Column(name = "campaign_id")
  private Long campaignId;

  @Column(name = "parent_external_id", length = 128)
  private String parentExternalId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();
}
