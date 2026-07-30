package com.shopmanagement.crmservice.persistence.entity;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "crm_audit_export_job")
@Getter
@Setter
public class CrmAuditExportJobEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id", nullable = false, length = 64)
  private String tenantId;

  @Column(nullable = false, length = 16)
  private String status = "QUEUED";

  @Column(nullable = false, length = 16)
  private String format = "JSON";

  @Column(name = "from_at")
  private Instant fromAt;

  @Column(name = "to_at")
  private Instant toAt;

  @Column(name = "requested_by", length = 64)
  private String requestedBy;

  @Column(name = "artifact_url", length = 512)
  private String artifactUrl;

  @Column(name = "row_count", nullable = false)
  private int rowCount;

  @Column(name = "error_message", length = 512)
  private String errorMessage;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "result_json", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> resultJson = new LinkedHashMap<>();

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "completed_at")
  private Instant completedAt;
}
