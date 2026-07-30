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
@Table(name = "crm_workspace")
@Getter
@Setter
public class CrmWorkspaceEntity extends TenantAuditableEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 128)
  private String name;

  @Column(name = "template_code", nullable = false, length = 64)
  private String templateCode = "GENERIC";

  @Column(nullable = false, length = 64)
  private String timezone = "Asia/Kolkata";

  @Column(nullable = false, length = 8)
  private String currency = "INR";

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "settings_json", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> settingsJson = new LinkedHashMap<>();
}
