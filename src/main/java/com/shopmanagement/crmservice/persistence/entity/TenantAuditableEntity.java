package com.shopmanagement.crmservice.persistence.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

@MappedSuperclass
@Getter
@Setter
public abstract class TenantAuditableEntity {

  @Column(name = "tenant_id", nullable = false, length = 64)
  private String tenantId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  @Column(name = "deleted_at")
  private Instant deletedAt;

  public boolean isDeleted() {
    return deletedAt != null;
  }

  public void softDelete() {
    deletedAt = Instant.now();
    updatedAt = Instant.now();
  }

  public void touch() {
    updatedAt = Instant.now();
  }
}
