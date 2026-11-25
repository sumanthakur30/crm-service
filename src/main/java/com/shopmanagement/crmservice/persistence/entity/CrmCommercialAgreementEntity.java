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
@Table(name = "crm_commercial_agreement")
@Getter
@Setter
public class CrmCommercialAgreementEntity extends TenantAuditableEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 32)
  private String party;

  @Column(name = "institution_id")
  private Long institutionId;

  @Column(name = "course_id")
  private Long courseId;

  @Column(name = "session_id")
  private Long sessionId;

  @Column(name = "consultant_id")
  private Long consultantId;

  @Column(nullable = false, length = 32)
  private String method;

  @Column(nullable = false, precision = 14, scale = 4)
  private BigDecimal rate;

  @Column(name = "base_kind", nullable = false, length = 32)
  private String baseKind;

  @Column(name = "trigger_code", nullable = false, length = 32)
  private String triggerCode;

  @Column(name = "effective_from", nullable = false)
  private LocalDate effectiveFrom;

  @Column(name = "effective_to")
  private LocalDate effectiveTo;

  @Column(nullable = false)
  private boolean active = true;
}
