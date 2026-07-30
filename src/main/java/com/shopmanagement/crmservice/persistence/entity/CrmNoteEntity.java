package com.shopmanagement.crmservice.persistence.entity;

import java.util.LinkedHashMap;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "crm_note")
@Getter
@Setter
public class CrmNoteEntity extends TenantAuditableEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "related_type", nullable = false, length = 32)
  private String relatedType = "LEAD";

  @Column(name = "related_id", nullable = false)
  private Long relatedId;

  @Column(nullable = false, columnDefinition = "text")
  private String body;

  @Column(name = "author_user_id", length = 64)
  private String authorUserId;
}
