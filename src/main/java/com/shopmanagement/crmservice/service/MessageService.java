package com.shopmanagement.crmservice.service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.shopmanagement.crmservice.api.CrmMessageApi.SendRequest;
import com.shopmanagement.crmservice.integration.NotificationClient;
import com.shopmanagement.crmservice.persistence.entity.CrmLeadEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmLeadRepository;
import com.shopmanagement.crmservice.support.TenantIds;

/** Sprint 7 — omnichannel compose → notification-service + lead timeline. */
@Service
public class MessageService {

  private static final Set<String> CHANNELS = Set.of("WHATSAPP", "SMS", "EMAIL");

  private final CrmLeadRepository leadRepository;
  private final NotificationClient notificationClient;
  private final TimelineService timelineService;

  public MessageService(
      CrmLeadRepository leadRepository,
      NotificationClient notificationClient,
      TimelineService timelineService) {
    this.leadRepository = leadRepository;
    this.notificationClient = notificationClient;
    this.timelineService = timelineService;
  }

  @Transactional
  public Map<String, Object> send(SendRequest body) {
    if (body == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Body required");
    }
    String tenantId = TenantIds.require();
    String channel = body.channel().trim().toUpperCase(Locale.ROOT);
    if (!CHANNELS.contains(channel)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "channel must be WHATSAPP, SMS, or EMAIL");
    }
    CrmLeadEntity lead =
        leadRepository
            .findByTenantIdAndIdAndDeletedAtIsNull(tenantId, body.leadId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead not found"));

    String recipient =
        body.recipient() != null && !body.recipient().isBlank()
            ? body.recipient().trim()
            : resolveRecipient(lead, channel);
    if (recipient == null || recipient.isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "recipient required (or set lead phone/email)");
    }

    String subject =
        body.subject() != null && !body.subject().isBlank()
            ? body.subject().trim()
            : "SugamFlow CRM · " + (lead.getTitle() == null ? "Lead" : lead.getTitle());
    String messageBody = body.body().trim();
    String template =
        body.templateCode() != null && !body.templateCode().isBlank()
            ? body.templateCode().trim()
            : null;

    Map<String, String> vars = new LinkedHashMap<>();
    vars.put("subject", subject);
    vars.put("body", messageBody);
    vars.put("recipient", recipient);
    vars.put("leadId", String.valueOf(lead.getId()));
    vars.put("name", lead.getDisplayName() == null ? "" : lead.getDisplayName());

    String idempotency = "crm-msg-lead-" + lead.getId() + "-" + UUID.randomUUID();
    Map<String, Object> delivery =
        notificationClient.queue(
            tenantId, channel, recipient, subject, messageBody, idempotency, template, vars);

    String status = String.valueOf(delivery.getOrDefault("status", "UNKNOWN"));
    String eventType =
        "ERROR".equalsIgnoreCase(status) || "FAILED".equalsIgnoreCase(status)
            ? "MESSAGE_FAILED"
            : "SKIPPED_DISABLED".equalsIgnoreCase(status) ? "MESSAGE_QUEUED" : "MESSAGE_QUEUED";
    if ("ERROR".equalsIgnoreCase(status)) {
      eventType = "MESSAGE_FAILED";
    }

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("channel", channel);
    payload.put("recipient", recipient);
    payload.put("status", status);
    if (delivery.get("notificationId") != null) {
      payload.put("notificationId", delivery.get("notificationId"));
    }
    if (delivery.get("error") != null) {
      payload.put("error", delivery.get("error"));
    }

    timelineService.recordEvent(
        "LEAD",
        lead.getId(),
        eventType,
        channel + " → " + recipient + " · " + status,
        payload);

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("leadId", lead.getId());
    out.put("channel", channel);
    out.put("recipient", recipient);
    out.put("eventType", eventType);
    out.put("delivery", delivery);
    return out;
  }

  private static String resolveRecipient(CrmLeadEntity lead, String channel) {
    if ("EMAIL".equals(channel)) {
      return lead.getEmail();
    }
    return lead.getPhone() != null && !lead.getPhone().isBlank() ? lead.getPhone() : lead.getEmail();
  }
}
