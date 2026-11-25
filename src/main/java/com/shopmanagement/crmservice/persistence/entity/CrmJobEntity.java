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
@Table(name = "crm_job")
@Getter
@Setter
public class CrmJobEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id", nullable = false, length = 64)
  private String tenantId;

  @Column(name = "job_type", nullable = false, length = 64)
  private String jobType;

  @Column(name = "ref_id")
  private Long refId;

  @Column(nullable = false, length = 16)
  private String status;

  @Column(name = "run_at", nullable = false)
  private Instant runAt = Instant.now();

  @Column(nullable = false)
  private int attempts;

  @Column(name = "last_error", length = 500)
  private String lastError;

  @Column(name = "locked_at")
  private Instant lockedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();
}
