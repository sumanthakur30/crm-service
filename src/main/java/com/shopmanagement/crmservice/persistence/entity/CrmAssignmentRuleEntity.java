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
@Table(name = "crm_assignment_rule")
@Getter
@Setter
public class CrmAssignmentRuleEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id", nullable = false, length = 64)
  private String tenantId;

  @Column(name = "source_code", length = 64)
  private String sourceCode;

  @Column(name = "course_code", length = 64)
  private String courseCode;

  @Column(length = 128)
  private String city;

  @Column(name = "owner_user_id", nullable = false, length = 64)
  private String ownerUserId;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder = 100;

  @Column(nullable = false)
  private boolean active = true;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();
}
