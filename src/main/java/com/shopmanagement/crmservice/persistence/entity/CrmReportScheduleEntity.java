package com.shopmanagement.crmservice.persistence.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
@Table(name = "crm_report_schedule")
@Getter
@Setter
public class CrmReportScheduleEntity extends TenantAuditableEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 64)
  private String code;

  @Column(nullable = false, length = 128)
  private String name;

  @Column(name = "report_type", nullable = false, length = 64)
  private String reportType;

  @Column(nullable = false, length = 16)
  private String frequency = "DAILY";

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "recipients_json", nullable = false, columnDefinition = "jsonb")
  private List<Object> recipientsJson = new ArrayList<>();

  @Column(nullable = false)
  private boolean active = true;

  @Column(name = "last_run_at")
  private Instant lastRunAt;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "last_result_json", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> lastResultJson = new LinkedHashMap<>();
}
