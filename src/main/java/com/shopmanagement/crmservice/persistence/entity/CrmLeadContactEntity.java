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
@Table(name = "crm_lead_contact")
@Getter
@Setter
public class CrmLeadContactEntity extends TenantAuditableEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "lead_id", nullable = false)
  private Long leadId;

  @Column(name = "contact_id", nullable = false)
  private Long contactId;

  @Column(nullable = false, length = 32)
  private String role;

  @Column(name = "is_primary", nullable = false)
  private boolean primaryContact;
}
