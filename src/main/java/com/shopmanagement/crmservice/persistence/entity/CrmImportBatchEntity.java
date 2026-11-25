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
@Table(name = "crm_import_batch")
@Getter
@Setter
public class CrmImportBatchEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id", nullable = false, length = 64)
  private String tenantId;

  @Column(nullable = false, length = 256)
  private String label;

  @Column(name = "uploaded_by", length = 64)
  private String uploadedBy;

  @Column(name = "row_count", nullable = false)
  private int rowCount;

  @Column(name = "accepted_count", nullable = false)
  private int acceptedCount;

  @Column(name = "rejected_count", nullable = false)
  private int rejectedCount;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();
}
