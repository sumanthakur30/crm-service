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
@Table(name = "crm_call_log")
@Getter
@Setter
public class CrmCallLogEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id", nullable = false, length = 64)
  private String tenantId;

  @Column(name = "lead_id")
  private Long leadId;

  @Column(nullable = false, length = 16)
  private String direction = "OUTBOUND";

  @Column(length = 64)
  private String phone;

  @Column(name = "duration_sec", nullable = false)
  private int durationSec = 0;

  @Column(length = 64)
  private String outcome;

  @Column(length = 32)
  private String provider;

  @Column(name = "provider_ref", length = 128)
  private String providerRef;

  @Column(columnDefinition = "text")
  private String notes;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt = Instant.now();

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();
}
