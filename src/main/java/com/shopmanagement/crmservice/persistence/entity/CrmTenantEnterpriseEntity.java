package com.shopmanagement.crmservice.persistence.entity;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "crm_tenant_enterprise")
@Getter
@Setter
public class CrmTenantEnterpriseEntity {

  @Id
  @Column(name = "tenant_id", length = 64)
  private String tenantId;

  @Column(name = "data_residency", nullable = false, length = 32)
  private String dataResidency = "IN";

  @Column(name = "sso_enabled", nullable = false)
  private boolean ssoEnabled;

  @Column(name = "sso_provider", length = 64)
  private String ssoProvider;

  @Column(name = "sso_metadata_url", length = 512)
  private String ssoMetadataUrl;

  @Column(name = "audit_export_enabled", nullable = false)
  private boolean auditExportEnabled = true;

  @Column(name = "ai_enabled", nullable = false)
  private boolean aiEnabled = true;

  @Column(name = "preferred_language", nullable = false, length = 16)
  private String preferredLanguage = "en";

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> attributes = new LinkedHashMap<>();

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();
}
