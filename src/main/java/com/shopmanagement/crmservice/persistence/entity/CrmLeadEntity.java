package com.shopmanagement.crmservice.persistence.entity;

import java.math.BigDecimal;
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
@Table(name = "crm_lead")
@Getter
@Setter
public class CrmLeadEntity extends TenantAuditableEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "pipeline_id", nullable = false)
  private Long pipelineId;

  @Column(name = "stage_id", nullable = false)
  private Long stageId;

  @Column(nullable = false, length = 256)
  private String title;

  @Column(name = "display_name", length = 256)
  private String displayName;

  @Column(name = "company_name", length = 256)
  private String companyName;

  @Column(length = 256)
  private String email;

  @Column(length = 64)
  private String phone;

  @Column(name = "source_code", length = 64)
  private String sourceCode;

  @Column(nullable = false, length = 32)
  private String status = "OPEN";

  @Column(nullable = false, length = 32)
  private String priority = "MEDIUM";

  @Column(nullable = false)
  private int score;

  @Column(name = "owner_user_id", length = 64)
  private String ownerUserId;

  @Column(name = "team_id", length = 64)
  private String teamId;

  @Column(precision = 18, scale = 2)
  private BigDecimal amount;

  @Column(nullable = false, length = 8)
  private String currency = "INR";

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> attributes = new LinkedHashMap<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "external_refs", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> externalRefs = new LinkedHashMap<>();

  @Column(name = "form_key", length = 128)
  private String formKey;

  @Column(name = "campaign_id")
  private Long campaignId;

  @Column(name = "utm_source", length = 128)
  private String utmSource;

  @Column(name = "utm_medium", length = 128)
  private String utmMedium;

  @Column(name = "utm_campaign", length = 128)
  private String utmCampaign;

  @Column(name = "utm_content", length = 128)
  private String utmContent;

  @Column(name = "utm_term", length = 128)
  private String utmTerm;
}
