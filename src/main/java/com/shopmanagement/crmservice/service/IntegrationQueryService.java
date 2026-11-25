package com.shopmanagement.crmservice.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopmanagement.crmservice.api.CrmIntegrationApi.IntegrationCard;
import com.shopmanagement.crmservice.persistence.entity.CrmInboundEventEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmWebFormEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmInboundEventRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmTouchRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmWebFormRepository;
import com.shopmanagement.crmservice.support.TenantIds;

/** Provider cards. The Meta card has no test-inject action. */
@Service
public class IntegrationQueryService {

  private final CrmWebFormRepository formRepository;
  private final CrmInboundEventRepository inboundRepository;
  private final CrmTouchRepository touchRepository;

  public IntegrationQueryService(
      CrmWebFormRepository formRepository,
      CrmInboundEventRepository inboundRepository,
      CrmTouchRepository touchRepository) {
    this.formRepository = formRepository;
    this.inboundRepository = inboundRepository;
    this.touchRepository = touchRepository;
  }

  @Transactional(readOnly = true)
  public List<IntegrationCard> list() {
    String tenantId = TenantIds.require();
    Instant since = Instant.now().minus(7, ChronoUnit.DAYS);
    List<CrmWebFormEntity> forms = formRepository.findByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc(tenantId);
    List<String> names = new ArrayList<>();
    for (CrmWebFormEntity form : forms) {
      if (names.size() == 8) {
        break;
      }
      names.add(form.getName());
    }
    List<CrmInboundEventEntity> recent =
        inboundRepository.findByTenantIdAndProviderOrderByCreatedAtDesc(tenantId, "WEB", PageRequest.of(0, 1));
    CrmInboundEventEntity latest = recent.isEmpty() ? null : recent.get(0);
    String status = forms.isEmpty() ? "DISCONNECTED" : "CONNECTED";
    String lastError = null;
    Instant lastSuccess = null;
    if (latest != null && "FAILED".equalsIgnoreCase(latest.getStatus())) {
      status = forms.isEmpty() ? "DISCONNECTED" : "DEGRADED";
      lastError = latest.getErrorMessage();
    } else if (latest != null && "MAPPED".equalsIgnoreCase(latest.getStatus())) {
      lastSuccess = latest.getProcessedAt();
    }
    long webLeads = touchRepository.countByTenantIdAndProviderAndReceivedAtAfter(tenantId, "WEB", since);
    long webFailed = inboundRepository.countFailedSince(tenantId, "WEB", since);
    long fileLeads = touchRepository.countByTenantIdAndProviderAndReceivedAtAfter(tenantId, "FILE", since);
    List<IntegrationCard> cards = new ArrayList<>();
    cards.add(
        new IntegrationCard(
            "WEBSITE",
            status,
            "Website forms",
            "A form posts to your capture link. The field map decides which answer is the mobile, the email, and the course.",
            forms.size(),
            names,
            lastSuccess,
            webLeads,
            webFailed,
            lastError,
            forms.isEmpty() ? "Add a form" : "Add another form"));
    cards.add(disconnected("META", "Facebook Lead Ads", "Not connected. A page is not receiving lead ads."));
    cards.add(
        disconnected(
            "GOOGLE_ADS",
            "Google lead forms",
            "Not connected. This card is lead forms. Analytics is separate."));
    cards.add(disconnected("WHATSAPP", "WhatsApp", "Not connected."));
    cards.add(
        disconnected(
            "TELEPHONY",
            "Calling",
            "Not connected. Log a call from the lead until a provider is connected."));
    cards.add(disconnected("PORTAL", "Portals", "Not connected."));
    cards.add(
        new IntegrationCard(
            "CSV",
            "CONNECTED",
            "CSV import",
            "Upload a file, map the columns, preview the errors, then commit. A matching mobile stays on the existing lead.",
            0,
            List.of(),
            null,
            fileLeads,
            0,
            null,
            "Open the wizard"));
    return cards;
  }

  private static IntegrationCard disconnected(String provider, String name, String summary) {
    return new IntegrationCard(provider, "DISCONNECTED", name, summary, 0, List.of(), null, 0, 0, null, "Not available yet");
  }
}
