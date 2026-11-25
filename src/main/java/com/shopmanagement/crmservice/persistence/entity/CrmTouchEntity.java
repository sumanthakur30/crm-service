package com.shopmanagement.crmservice.persistence.entity;

import java.time.Instant;
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
@Table(name = "crm_touch")
@Getter
@Setter
public class CrmTouchEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id", nullable = false, length = 64)
  private String tenantId;

  @Column(name = "lead_id", nullable = false)
  private Long leadId;

  @Column(name = "contact_id")
  private Long contactId;

  @Column(name = "source_code", nullable = false, length = 64)
  private String sourceCode;

  @Column(name = "source_category", nullable = false, length = 32)
  private String sourceCategory;

  @Column(nullable = false, length = 32)
  private String provider;

  @Column(name = "external_id", length = 128)
  private String externalId;

  @Column(name = "campaign_id")
  private Long campaignId;

  @Column(name = "branch_id")
  private Long branchId;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt = Instant.now();

  @Column(name = "received_at", nullable = false)
  private Instant receivedAt = Instant.now();

  @Column(name = "raw_event_id")
  private Long rawEventId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "field_values", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> fieldValues = new LinkedHashMap<>();
}
