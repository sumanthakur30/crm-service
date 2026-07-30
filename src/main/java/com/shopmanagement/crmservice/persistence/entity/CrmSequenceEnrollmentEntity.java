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
@Table(name = "crm_sequence_enrollment")
@Getter
@Setter
public class CrmSequenceEnrollmentEntity extends TenantAuditableEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "sequence_id", nullable = false)
  private Long sequenceId;

  @Column(name = "lead_id")
  private Long leadId;

  @Column(name = "opportunity_id")
  private Long opportunityId;

  @Column(nullable = false, length = 256)
  private String recipient;

  @Column(nullable = false, length = 16)
  private String channel;

  @Column(nullable = false, length = 16)
  private String status = "ACTIVE";

  @Column(name = "current_step", nullable = false)
  private int currentStep = 0;

  @Column(name = "next_run_at", nullable = false)
  private Instant nextRunAt = Instant.now();

  @Column(name = "last_error", length = 512)
  private String lastError;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> attributes = new LinkedHashMap<>();

  @Column(name = "completed_at")
  private Instant completedAt;
}
