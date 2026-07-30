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
@Table(name = "crm_object_tag")
@Getter
@Setter
public class CrmObjectTagEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id", nullable = false, length = 64)
  private String tenantId;

  @Column(name = "object_type", nullable = false, length = 32)
  private String objectType;

  @Column(name = "object_id", nullable = false)
  private Long objectId;

  @Column(name = "tag_id", nullable = false)
  private Long tagId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();
}
