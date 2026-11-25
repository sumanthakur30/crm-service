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
@Table(name = "crm_integration")
@Getter
@Setter
public class CrmIntegrationEntity extends TenantAuditableEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 32)
  private String provider;

  @Column(nullable = false, length = 16)
  private String status = "DISCONNECTED";

  @Column(name = "display_name", length = 160)
  private String displayName;

  @Column(name = "connected_at")
  private Instant connectedAt;

  @Column(name = "last_success_at")
  private Instant lastSuccessAt;

  @Column(name = "last_error", length = 500)
  private String lastError;

  @Column(name = "last_token_check_at")
  private Instant lastTokenCheckAt;
}
