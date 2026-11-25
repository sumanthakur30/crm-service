package com.shopmanagement.crmservice.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "crm_attribution")
@Getter
@Setter
public class CrmAttributionEntity {

  @Id
  @Column(name = "touch_id")
  private Long touchId;

  @Column(name = "campaign_external_id", length = 128)
  private String campaignExternalId;

  @Column(name = "campaign_name", length = 256)
  private String campaignName;

  @Column(name = "adset_external_id", length = 128)
  private String adsetExternalId;

  @Column(name = "adset_name", length = 256)
  private String adsetName;

  @Column(name = "ad_external_id", length = 128)
  private String adExternalId;

  @Column(name = "ad_name", length = 256)
  private String adName;

  @Column(name = "form_external_id", length = 128)
  private String formExternalId;

  @Column(name = "form_name", length = 256)
  private String formName;

  @Column(length = 256)
  private String keyword;

  @Column(name = "landing_url", length = 512)
  private String landingUrl;

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

  @Column(length = 256)
  private String gclid;

  @Column(length = 256)
  private String gbraid;

  @Column(length = 256)
  private String wbraid;

  @Column(name = "ctwa_clid", length = 256)
  private String ctwaClid;

  @Column(name = "page_external_id", length = 128)
  private String pageExternalId;

  @Column(name = "instagram_actor_id", length = 128)
  private String instagramActorId;
}
