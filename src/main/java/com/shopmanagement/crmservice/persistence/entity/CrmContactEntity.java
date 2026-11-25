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
@Table(name = "crm_contact")
@Getter
@Setter
public class CrmContactEntity extends TenantAuditableEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "account_id")
  private Long accountId;

  @Column(name = "display_name", nullable = false, length = 256)
  private String displayName;

  @Column(length = 256)
  private String email;

  @Column(length = 64)
  private String phone;

  @Column(length = 128)
  private String title;

  @Column(name = "first_name", length = 128)
  private String firstName;

  @Column(name = "last_name", length = 128)
  private String lastName;

  @Column(name = "phone_e164", length = 32)
  private String phoneE164;

  @Column(name = "phone_raw", length = 64)
  private String phoneRaw;

  @Column(name = "alternate_phone_e164", length = 32)
  private String alternatePhoneE164;

  @Column(name = "email_normalized", length = 256)
  private String emailNormalized;

  @Column(length = 128)
  private String city;

  @Column(name = "state_code", length = 8)
  private String stateCode;

  @Column(name = "contact_kind", nullable = false, length = 32)
  private String contactKind = "OTHER";

  @Column(name = "do_not_contact", nullable = false)
  private boolean doNotContact;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> attributes = new LinkedHashMap<>();
}
