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
@Table(name = "crm_branch")
@Getter
@Setter
public class CrmBranchEntity extends TenantAuditableEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "institution_id", nullable = false)
  private Long institutionId;

  @Column(nullable = false, length = 64)
  private String code;

  @Column(nullable = false, length = 256)
  private String name;

  @Column(length = 128)
  private String city;

  @Column(name = "state_code", length = 8)
  private String stateCode;

  @Column(length = 16)
  private String pincode;

  @Column(nullable = false, length = 64)
  private String timezone = "Asia/Kolkata";

  @Column(nullable = false)
  private boolean active = true;
}
