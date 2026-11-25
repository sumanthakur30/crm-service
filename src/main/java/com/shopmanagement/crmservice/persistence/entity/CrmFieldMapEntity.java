package com.shopmanagement.crmservice.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "crm_field_map")
@Getter
@Setter
public class CrmFieldMapEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id", nullable = false, length = 64)
  private String tenantId;

  @Column(name = "form_id")
  private Long formId;

  @Column(name = "asset_id")
  private Long assetId;

  @Column(name = "external_key", nullable = false, length = 128)
  private String externalKey;

  @Column(nullable = false, length = 64)
  private String target;

  @Column(nullable = false)
  private boolean required;
}
