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
@Table(name = "crm_course")
@Getter
@Setter
public class CrmCourseEntity extends TenantAuditableEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 64)
  private String code;

  @Column(nullable = false, length = 256)
  private String name;

  @Column(length = 128)
  private String category;

  @Column(name = "duration_text", length = 64)
  private String durationText;

  @Column(nullable = false)
  private boolean active = true;
}
