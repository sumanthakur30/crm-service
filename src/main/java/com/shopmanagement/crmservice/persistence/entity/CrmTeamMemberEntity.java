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
@Table(name = "crm_team_member")
@Getter
@Setter
public class CrmTeamMemberEntity extends TenantAuditableEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "team_id", nullable = false, length = 64)
  private String teamId = "DEFAULT";

  @Column(name = "user_id", nullable = false, length = 64)
  private String userId;

  @Column(name = "display_name", length = 128)
  private String displayName;

  @Column(nullable = false)
  private boolean active = true;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(name = "state_codes", nullable = false, columnDefinition = "TEXT")
  private String stateCodes = "";

  @Column(name = "pincode_prefixes", nullable = false, columnDefinition = "TEXT")
  private String pincodePrefixes = "";

  @Column(name = "open_lead_cap", nullable = false)
  private int openLeadCap;
}
