package com.shopmanagement.crmservice.persistence.entity;

import java.util.ArrayList;
import java.util.List;

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
@Table(name = "crm_field_def")
@Getter
@Setter
public class CrmFieldDefEntity extends TenantAuditableEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "object_type", nullable = false, length = 32)
  private String objectType;

  @Column(nullable = false, length = 64)
  private String code;

  @Column(nullable = false, length = 128)
  private String label;

  @Column(name = "data_type", nullable = false, length = 32)
  private String dataType;

  @Column(nullable = false)
  private boolean required;

  @Column(nullable = false)
  private boolean archived;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private List<String> options = new ArrayList<>();
}
