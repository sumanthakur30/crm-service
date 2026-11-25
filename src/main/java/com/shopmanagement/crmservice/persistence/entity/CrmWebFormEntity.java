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
@Table(name = "crm_web_form")
@Getter
@Setter
public class CrmWebFormEntity extends TenantAuditableEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "public_key", nullable = false, length = 64)
  private String publicKey;

  @Column(nullable = false, length = 160)
  private String name;

  @Column(name = "branch_id")
  private Long branchId;

  @Column(name = "campaign_id")
  private Long campaignId;

  @Column(name = "default_course", length = 160)
  private String defaultCourse;

  @Column(nullable = false, length = 16)
  private String status = "TEST";

  @Column(name = "honeypot_field", nullable = false, length = 64)
  private String honeypotField = "company_website";
}
