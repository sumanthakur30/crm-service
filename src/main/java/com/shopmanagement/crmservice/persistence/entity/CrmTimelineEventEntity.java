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
@Table(name = "crm_timeline_event")
@Getter
@Setter
public class CrmTimelineEventEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id", nullable = false, length = 64)
  private String tenantId;

  @Column(name = "related_type", nullable = false, length = 32)
  private String relatedType = "LEAD";

  @Column(name = "related_id", nullable = false)
  private Long relatedId;

  @Column(name = "event_type", nullable = false, length = 64)
  private String eventType;

  @Column(nullable = false, length = 512)
  private String summary;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> payloadJson = new LinkedHashMap<>();

  @Column(name = "actor_user_id", length = 64)
  private String actorUserId;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt = Instant.now();
}
