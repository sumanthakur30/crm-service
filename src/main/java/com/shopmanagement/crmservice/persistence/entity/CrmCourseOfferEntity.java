package com.shopmanagement.crmservice.persistence.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "crm_course_offer")
@Getter
@Setter
public class CrmCourseOfferEntity extends TenantAuditableEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "institution_id", nullable = false)
  private Long institutionId;

  @Column(name = "course_id", nullable = false)
  private Long courseId;

  @Column(name = "session_id", nullable = false)
  private Long sessionId;

  @Column(name = "fee_amount", nullable = false, precision = 14, scale = 2)
  private BigDecimal feeAmount = BigDecimal.ZERO;

  @Column(nullable = false)
  private boolean active = true;
}
