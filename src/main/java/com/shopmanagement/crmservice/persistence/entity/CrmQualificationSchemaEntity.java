package com.shopmanagement.crmservice.persistence.entity;

import java.util.ArrayList;
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
@Table(name = "crm_qualification_schema")
@Getter
@Setter
public class CrmQualificationSchemaEntity extends TenantAuditableEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 64)
  private String code;

  @Column(nullable = false, length = 128)
  private String name;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "fields_json", nullable = false, columnDefinition = "jsonb")
  private List<Map<String, Object>> fieldsJson = new ArrayList<>();

  @Column(nullable = false)
  private boolean active = true;
}
