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
@Table(name = "crm_approval")
@Getter
@Setter
public class CrmApprovalEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id", nullable = false, length = 64)
  private String tenantId;

  @Column(name = "object_type", nullable = false, length = 32)
  private String objectType;

  @Column(name = "object_id", nullable = false)
  private Long objectId;

  @Column(nullable = false, length = 256)
  private String title;

  @Column(nullable = false, length = 16)
  private String status = "PENDING";

  @Column(name = "requested_by", length = 64)
  private String requestedBy;

  @Column(name = "decided_by", length = 64)
  private String decidedBy;

  @Column(name = "decision_note", length = 512)
  private String decisionNote;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> attributes = new LinkedHashMap<>();

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  @Column(name = "decided_at")
  private Instant decidedAt;
}
