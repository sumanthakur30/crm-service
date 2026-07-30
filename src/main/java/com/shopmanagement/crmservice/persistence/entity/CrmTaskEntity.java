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
@Table(name = "crm_task")
@Getter
@Setter
public class CrmTaskEntity extends TenantAuditableEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "related_type", nullable = false, length = 32)
  private String relatedType;

  @Column(name = "related_id", nullable = false)
  private Long relatedId;

  @Column(nullable = false, length = 256)
  private String title;

  @Column(nullable = false, length = 16)
  private String status = "OPEN";

  @Column(nullable = false, length = 16)
  private String priority = "MEDIUM";

  @Column(name = "due_at")
  private Instant dueAt;

  @Column(name = "owner_user_id", length = 64)
  private String ownerUserId;

  @Column(name = "source_code", length = 64)
  private String sourceCode;

  @Column(name = "sla_policy_id")
  private Long slaPolicyId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> attributes = new LinkedHashMap<>();

  @Column(name = "completed_at")
  private Instant completedAt;
}
