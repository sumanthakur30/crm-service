package com.shopmanagement.crmservice.persistence.entity;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
    name = "crm_forecast_commit",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_crm_forecast_commit",
            columnNames = {"tenant_id", "user_id", "period_ym"}))
@Getter
@Setter
public class CrmForecastCommitEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id", nullable = false, length = 64)
  private String tenantId;

  @Column(name = "user_id", nullable = false, length = 128)
  private String userId;

  @Column(name = "period_ym", nullable = false, length = 7)
  private String periodYm;

  @Column(nullable = false, precision = 18, scale = 2)
  private BigDecimal amount;

  @Column(nullable = false, length = 8)
  private String currency = "INR";

  @Column(length = 512)
  private String note;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();
}
