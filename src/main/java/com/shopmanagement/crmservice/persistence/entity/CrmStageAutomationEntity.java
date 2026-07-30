package com.shopmanagement.crmservice.persistence.entity;

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
@Table(name = "crm_stage_automation")
@Getter
@Setter
public class CrmStageAutomationEntity extends TenantAuditableEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** LEAD or OPPORTUNITY */
  @Column(name = "object_type", nullable = false, length = 32)
  private String objectType;

  @Column(name = "to_stage_code", length = 64)
  private String toStageCode;

  @Column(name = "to_stage_id")
  private Long toStageId;

  /** CREATE_TASK | TIMELINE_NOTE */
  @Column(name = "action_type", nullable = false, length = 32)
  private String actionType;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "action_config", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> actionConfig = new LinkedHashMap<>();

  @Column(nullable = false)
  private boolean active = true;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;
}
