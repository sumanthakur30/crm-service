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
@Table(name = "crm_account")
@Getter
@Setter
public class CrmAccountEntity extends TenantAuditableEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 256)
  private String name;

  @Column(length = 32)
  private String gstin;

  @Column(length = 64)
  private String phone;

  @Column(length = 256)
  private String email;

  @Column(name = "state_code", length = 8)
  private String stateCode;

  @Column(length = 16)
  private String pincode;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> attributes = new LinkedHashMap<>();
}
