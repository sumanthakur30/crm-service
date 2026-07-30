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
@Table(name = "crm_sequence_step")
@Getter
@Setter
public class CrmSequenceStepEntity extends TenantAuditableEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "sequence_id", nullable = false)
  private Long sequenceId;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder = 10;

  @Column(name = "delay_hours", nullable = false)
  private int delayHours = 0;

  @Column(nullable = false, length = 16)
  private String channel;

  @Column(name = "subject_template", length = 256)
  private String subjectTemplate;

  @Column(name = "body_template", nullable = false, columnDefinition = "text")
  private String bodyTemplate;

  @Column(nullable = false)
  private boolean active = true;
}
