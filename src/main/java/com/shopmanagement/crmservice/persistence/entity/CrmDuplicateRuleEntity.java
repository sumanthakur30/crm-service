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
@Table(name = "crm_duplicate_rule")
@Getter
@Setter
public class CrmDuplicateRuleEntity extends TenantAuditableEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 64)
  private String code;

  @Column(name = "object_type", nullable = false, length = 32)
  private String objectType = "LEAD";

  @Column(name = "match_field", nullable = false, length = 32)
  private String matchField;

  @Column(name = "normalize_mode", nullable = false, length = 32)
  private String normalizeMode = "EXACT";

  @Column(nullable = false)
  private boolean enabled = true;

  @Column(nullable = false)
  private int weight = 10;
}
