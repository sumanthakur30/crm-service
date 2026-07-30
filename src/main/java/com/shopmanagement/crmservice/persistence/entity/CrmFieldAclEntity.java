package com.shopmanagement.crmservice.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "crm_field_acl")
@Getter
@Setter
public class CrmFieldAclEntity extends TenantAuditableEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "role_code", nullable = false, length = 64)
  private String roleCode;

  @Column(name = "object_type", nullable = false, length = 32)
  private String objectType;

  @Column(name = "field_name", nullable = false, length = 64)
  private String fieldName;

  @Column(nullable = false, length = 16)
  private String access = "READ";
}
