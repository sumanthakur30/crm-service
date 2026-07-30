package com.shopmanagement.crmservice.persistence.entity;

import java.time.Instant;
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
@Table(name = "crm_campaign")
@Getter
@Setter
public class CrmCampaignEntity extends TenantAuditableEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 64)
  private String code;

  @Column(nullable = false, length = 128)
  private String name;

  @Column(nullable = false, length = 16)
  private String status = "ACTIVE";

  @Column(length = 32)
  private String channel;

  @Column(name = "utm_source", length = 128)
  private String utmSource;

  @Column(name = "utm_medium", length = 128)
  private String utmMedium;

  @Column(name = "utm_campaign", length = 128)
  private String utmCampaign;

  @Column(name = "utm_content", length = 128)
  private String utmContent;

  @Column(name = "utm_term", length = 128)
  private String utmTerm;

  @Column(name = "landing_url", length = 512)
  private String landingUrl;

  @Column(name = "public_key", nullable = false, length = 64)
  private String publicKey;

  @Column(name = "starts_at")
  private Instant startsAt;

  @Column(name = "ends_at")
  private Instant endsAt;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> attributes = new LinkedHashMap<>();
}
