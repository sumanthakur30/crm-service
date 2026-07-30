package com.shopmanagement.crmservice.persistence.entity;

import java.math.BigDecimal;
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
@Table(name = "crm_ai_insight")
@Getter
@Setter
public class CrmAiInsightEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id", nullable = false, length = 64)
  private String tenantId;

  @Column(name = "related_type", nullable = false, length = 32)
  private String relatedType;

  @Column(name = "related_id", nullable = false)
  private Long relatedId;

  @Column(name = "insight_type", nullable = false, length = 32)
  private String insightType;

  @Column(nullable = false, length = 256)
  private String title;

  @Column(nullable = false, columnDefinition = "text")
  private String body;

  @Column(nullable = false, precision = 5, scale = 2)
  private BigDecimal confidence = BigDecimal.ZERO;

  @Column(name = "model_code", nullable = false, length = 64)
  private String modelCode = "HEURISTIC_V1";

  @Column(name = "language_code", nullable = false, length = 16)
  private String languageCode = "en";

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> payloadJson = new LinkedHashMap<>();

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();
}
