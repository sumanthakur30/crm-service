package com.shopmanagement.crmservice.persistence.entity;

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
@Table(name = "crm_placement")
@Getter
@Setter
public class CrmPlacementEntity extends TenantAuditableEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "lead_id", nullable = false)
  private Long leadId;

  @Column(name = "contact_id")
  private Long contactId;

  @Column(name = "institution_id", nullable = false)
  private Long institutionId;

  @Column(name = "course_id", nullable = false)
  private Long courseId;

  @Column(name = "session_id", nullable = false)
  private Long sessionId;

  @Column(name = "consultant_id")
  private Long consultantId;

  @Column(name = "application_no", nullable = false, length = 32)
  private String applicationNo;

  @Column(nullable = false, length = 32)
  private String status = "DRAFT";

  @Column(name = "enrolment_no", length = 64)
  private String enrolmentNo;

  @Column(name = "confirmed_on")
  private LocalDate confirmedOn;

  @Column(name = "cancelled_on")
  private LocalDate cancelledOn;

  @Column(name = "cancel_reason", length = 500)
  private String cancelReason;
}
