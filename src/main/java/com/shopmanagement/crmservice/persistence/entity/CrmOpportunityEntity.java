package com.shopmanagement.crmservice.persistence.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "crm_opportunity")
@Getter
@Setter
public class CrmOpportunityEntity extends TenantAuditableEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "pipeline_id", nullable = false)
  private Long pipelineId;

  @Column(name = "stage_id", nullable = false)
  private Long stageId;

  @Column(name = "lead_id")
  private Long leadId;

  @Column(name = "account_id")
  private Long accountId;

  @Column(name = "close_reason_code", length = 64)
  private String closeReasonCode;

  @Column(name = "close_reason_note", length = 512)
  private String closeReasonNote;

  @Column(nullable = false, length = 256)
  private String name;

  @Column(precision = 18, scale = 2)
  private BigDecimal amount;

  @Column(nullable = false, length = 8)
  private String currency = "INR";

  @Column(nullable = false)
  private int probability;

  @Column(name = "expected_close_date")
  private LocalDate expectedCloseDate;

  @Column(nullable = false, length = 32)
  private String status = "OPEN";

  @Column(name = "owner_user_id", length = 64)
  private String ownerUserId;

  @Column(name = "team_id", length = 64)
  private String teamId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> attributes = new LinkedHashMap<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "external_refs", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> externalRefs = new LinkedHashMap<>();
}
