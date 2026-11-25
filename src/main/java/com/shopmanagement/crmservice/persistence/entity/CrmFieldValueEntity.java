package com.shopmanagement.crmservice.persistence.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "crm_field_value")
@Getter
@Setter
public class CrmFieldValueEntity extends TenantAuditableEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "object_type", nullable = false, length = 32)
  private String objectType;

  @Column(name = "object_id", nullable = false)
  private Long objectId;

  @Column(name = "field_def_id", nullable = false)
  private Long fieldDefId;

  @Column(name = "value_text", length = 512)
  private String valueText;

  @Column(name = "value_number", precision = 18, scale = 4)
  private BigDecimal valueNumber;

  @Column(name = "value_date")
  private LocalDate valueDate;
}
