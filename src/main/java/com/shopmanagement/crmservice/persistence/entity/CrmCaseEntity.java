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
@Table(name = "crm_case")
@Getter
@Setter
public class CrmCaseEntity extends TenantAuditableEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 512)
  private String subject;

  /** OPEN, PENDING, RESOLVED, CLOSED */
  @Column(nullable = false, length = 16)
  private String status = "OPEN";

  /** LOW, MEDIUM, HIGH, URGENT */
  @Column(nullable = false, length = 16)
  private String priority = "MEDIUM";

  @Column(name = "related_lead_id")
  private Long relatedLeadId;

  @Column(name = "related_opportunity_id")
  private Long relatedOpportunityId;

  @Column(name = "assigned_to", length = 128)
  private String assignedTo;

  @JdbcTypeCode(SqlTypes.SMALLINT)
  @Column(name = "csat_score")
  private Integer csatScore;

  @Column(name = "csat_comment", length = 1024)
  private String csatComment;

  @Column(name = "csat_submitted_at")
  private Instant csatSubmittedAt;
}

