package com.shopmanagement.crmservice.persistence.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "crm_csat_response")
@Getter
@Setter
public class CrmCsatResponseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id", nullable = false, length = 64)
  private String tenantId;

  @Column(name = "case_id", nullable = false)
  private Long caseId;

  @Column(nullable = false)
  @JdbcTypeCode(SqlTypes.SMALLINT)
  private Integer score;

  @Column(length = 1024)
  private String comment;

  @Column(name = "submitted_by", length = 128)
  private String submittedBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();
}

