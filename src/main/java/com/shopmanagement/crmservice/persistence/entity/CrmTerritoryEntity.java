package com.shopmanagement.crmservice.persistence.entity;

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
@Table(name = "crm_territory")
@Getter
@Setter
public class CrmTerritoryEntity extends TenantAuditableEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 64)
  private String code;

  @Column(nullable = false, length = 200)
  private String name;

  @Column(name = "region_code", length = 64)
  private String regionCode;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "geo_json", columnDefinition = "jsonb")
  private Map<String, Object> geoJson;
}
