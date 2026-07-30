package com.shopmanagement.crmservice.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.shopmanagement.crmservice.api.CrmCampaignApi.PublicCaptureRequest;
import com.shopmanagement.crmservice.api.CrmLeadApi.LeadResponse;
import com.shopmanagement.crmservice.filter.TenantContextFilter;
import com.shopmanagement.crmservice.persistence.entity.CrmCampaignEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmInboundEventEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmCampaignRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmInboundEventRepository;
import com.shopmanagement.crmservice.support.TenantIds;

@Service
public class InboundAdapterService {

  private final CrmInboundEventRepository inboundRepository;
  private final CrmCampaignRepository campaignRepository;
  private final CampaignService campaignService;
  private final CrmLeadService leadService;
  private final BehaviorScoringService scoringService;
  private final OpsService opsService;

  public InboundAdapterService(
      CrmInboundEventRepository inboundRepository,
      CrmCampaignRepository campaignRepository,
      CampaignService campaignService,
      CrmLeadService leadService,
      BehaviorScoringService scoringService,
      OpsService opsService) {
    this.inboundRepository = inboundRepository;
    this.campaignRepository = campaignRepository;
    this.campaignService = campaignService;
    this.leadService = leadService;
    this.scoringService = scoringService;
    this.opsService = opsService;
  }

  @Transactional
  public Map<String, Object> ingest(
      String provider, String tenantId, String publicKey, Map<String, Object> payload) {
    String prov = provider.trim().toUpperCase(Locale.ROOT);
    String externalId = firstString(payload, "externalId", "lead_id", "id", "callId", "messageId");
    if (externalId != null) {
      var existing = inboundRepository.findByProviderAndExternalId(prov, externalId);
      if (existing.isPresent()) {
        Map<String, Object> dup = toEvent(existing.get());
        dup.put("duplicate", true);
        return dup;
      }
    }

    CrmInboundEventEntity event = new CrmInboundEventEntity();
    event.setProvider(prov);
    event.setExternalId(externalId);
    event.setStatus("RECEIVED");
    event.setPayloadJson(new LinkedHashMap<>(payload == null ? Map.of() : payload));

    String resolvedTenant = tenantId;
    Long campaignId = null;
    if (publicKey != null && !publicKey.isBlank()) {
      CrmCampaignEntity campaign =
          campaignRepository
              .findByPublicKeyAndDeletedAtIsNull(publicKey.trim())
              .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid publicKey"));
      resolvedTenant = campaign.getTenantId();
      campaignId = campaign.getId();
      event.setCampaignId(campaignId);
    }
    if (resolvedTenant == null || resolvedTenant.isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "tenantId or publicKey required for adapter ingest");
    }
    event.setTenantId(resolvedTenant);
    event = inboundRepository.save(event);

    TenantContextFilter.bindTenant(resolvedTenant);
    try {
      LeadResponse lead =
          switch (prov) {
            case "META", "GOOGLE" -> mapAdLead(prov, campaignId, publicKey, payload);
            case "MISSED_CALL" -> mapMissedCall(payload);
            case "CHATBOT" -> mapChatbot(campaignId, publicKey, payload);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported provider");
          };
      event.setLeadId(lead.id());
      event.setStatus("MAPPED");
      event.setProcessedAt(Instant.now());
      inboundRepository.save(event);
      Map<String, Object> out = toEvent(event);
      out.put("leadId", lead.id());
      out.put("leadTitle", lead.title());
      return out;
    } catch (RuntimeException ex) {
      event.setStatus("FAILED");
      event.setErrorMessage(
          ex.getMessage() == null
              ? "failed"
              : ex.getMessage().substring(0, Math.min(500, ex.getMessage().length())));
      event.setProcessedAt(Instant.now());
      inboundRepository.save(event);
      throw ex;
    } finally {
      TenantContextFilter.clearTenantForTests();
    }
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> recent(int limit) {
    return inboundRepository
        .findByTenantIdOrderByCreatedAtDesc(TenantIds.require(), PageRequest.of(0, Math.min(100, Math.max(1, limit))))
        .stream()
        .map(InboundAdapterService::toEvent)
        .toList();
  }

  private LeadResponse mapAdLead(
      String provider, Long campaignId, String publicKey, Map<String, Object> payload) {
    String title =
        firstString(payload, "full_name", "name", "title", "ad_name");
    if (title == null) {
      title = provider + " lead " + UUID.randomUUID().toString().substring(0, 8);
    }
    if (publicKey != null && !publicKey.isBlank()) {
      LeadResponse lead =
          campaignService.capturePublic(
              publicKey,
              new PublicCaptureRequest(
                  title,
                  firstString(payload, "full_name", "name"),
                  firstString(payload, "company_name", "company"),
                  firstString(payload, "email"),
                  firstString(payload, "phone_number", "phone"),
                  provider.toLowerCase(Locale.ROOT),
                  "paid",
                  firstString(payload, "campaign_name", "utm_campaign"),
                  firstString(payload, "ad_name", "utm_content"),
                  firstString(payload, "keyword", "utm_term"),
                  Map.of("provider", provider, "raw", payload)));
      scoringService.applyEvent(lead.id(), "CAMPAIGN_CAPTURE", provider + " capture", Map.of("provider", provider));
      return lead;
    }
    // Authenticated path with tenant already bound
    return leadService.create(
        new com.shopmanagement.crmservice.api.CrmLeadApi.LeadUpsert(
            title,
            firstString(payload, "full_name", "name"),
            firstString(payload, "company_name", "company"),
            firstString(payload, "email"),
            firstString(payload, "phone_number", "phone"),
            provider,
            "OPEN",
            "MEDIUM",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            Map.of("provider", provider),
            Map.of(),
            provider.toLowerCase(Locale.ROOT) + "-adapter",
            campaignId,
            provider.toLowerCase(Locale.ROOT),
            "paid",
            firstString(payload, "campaign_name"),
            firstString(payload, "ad_name"),
            firstString(payload, "keyword")));
  }

  private LeadResponse mapMissedCall(Map<String, Object> payload) {
    String phone = firstString(payload, "phone", "from", "caller");
    String title = "Missed call" + (phone == null ? "" : " from " + phone);
    LeadResponse lead =
        leadService.create(
            new com.shopmanagement.crmservice.api.CrmLeadApi.LeadUpsert(
                title,
                firstString(payload, "name"),
                null,
                firstString(payload, "email"),
                phone,
                "MISSED_CALL",
                "OPEN",
                "HIGH",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Map.of("provider", "MISSED_CALL"),
                Map.of(),
                "missed-call",
                null,
                "telephony",
                "missed_call",
                null,
                null,
                null));
    opsService.logCall(
        Map.of(
            "leadId",
            lead.id(),
            "direction",
            "MISSED",
            "phone",
            phone == null ? "" : phone,
            "outcome",
            "MISSED",
            "provider",
            "TELEPHONY",
            "providerRef",
            firstString(payload, "callId", "externalId") == null
                ? ""
                : firstString(payload, "callId", "externalId")));
    return lead;
  }

  private LeadResponse mapChatbot(Long campaignId, String publicKey, Map<String, Object> payload) {
    String title = firstString(payload, "intent", "title", "message");
    if (title == null) {
      title = "Chatbot conversation";
    }
    if (publicKey != null && !publicKey.isBlank()) {
      return campaignService.capturePublic(
          publicKey,
          new PublicCaptureRequest(
              title,
              firstString(payload, "name", "displayName"),
              null,
              firstString(payload, "email"),
              firstString(payload, "phone"),
              "chatbot",
              "conversation",
              firstString(payload, "botId"),
              null,
              null,
              Map.of("provider", "CHATBOT", "raw", payload)));
    }
    return leadService.create(
        new com.shopmanagement.crmservice.api.CrmLeadApi.LeadUpsert(
            title,
            firstString(payload, "name"),
            null,
            firstString(payload, "email"),
            firstString(payload, "phone"),
            "CHATBOT",
            "OPEN",
            "MEDIUM",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            Map.of("provider", "CHATBOT"),
            Map.of(),
            "chatbot",
            campaignId,
            "chatbot",
            "conversation",
            null,
            null,
            null));
  }

  private static Map<String, Object> toEvent(CrmInboundEventEntity e) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", e.getId());
    m.put("tenantId", e.getTenantId());
    m.put("provider", e.getProvider());
    m.put("externalId", e.getExternalId());
    m.put("status", e.getStatus());
    m.put("leadId", e.getLeadId());
    m.put("campaignId", e.getCampaignId());
    m.put("errorMessage", e.getErrorMessage());
    m.put("createdAt", e.getCreatedAt());
    return m;
  }

  private static String firstString(Map<String, Object> payload, String... keys) {
    if (payload == null) {
      return null;
    }
    for (String key : keys) {
      Object v = payload.get(key);
      if (v != null && !String.valueOf(v).isBlank()) {
        return String.valueOf(v).trim();
      }
    }
    return null;
  }
}
