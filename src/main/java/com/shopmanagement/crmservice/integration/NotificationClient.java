package com.shopmanagement.crmservice.integration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.shopmanagement.crmservice.config.CrmNotificationProperties;

@Component
public class NotificationClient {

  private static final Logger log = LoggerFactory.getLogger(NotificationClient.class);

  private final RestTemplate restTemplate;
  private final CrmNotificationProperties properties;

  public NotificationClient(RestTemplate crmRestTemplate, CrmNotificationProperties properties) {
    this.restTemplate = crmRestTemplate;
    this.properties = properties;
  }

  public boolean isEnabled() {
    return properties.isEnabled();
  }

  /**
   * Queues a notification via notification-service.
   *
   * @return delivery info map (status, notificationId, …)
   */
  public Map<String, Object> queue(
      String tenantId, String channel, String recipient, String subject, String body, String idempotencyKey) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("channel", channel);
    result.put("recipient", recipient);

    if (!properties.isEnabled()) {
      result.put("status", "SKIPPED_DISABLED");
      result.put("note", "crm.notification.enabled=false — enable to call notification-service");
      return result;
    }

    String url = trimSlash(properties.getBaseUrl()) + "/api/v1/notifications";
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("shopId", tenantId);
    payload.put("channel", channel.toUpperCase());
    payload.put("recipient", recipient);
    payload.put("subject", subject);
    payload.put("body", body);
    payload.put(
        "idempotencyKey",
        idempotencyKey == null || idempotencyKey.isBlank()
            ? "crm-quote-" + UUID.randomUUID()
            : idempotencyKey);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("X-Tenant-Id", tenantId);
    headers.set("X-Shop-Id", tenantId);

    try {
      ResponseEntity<Map<String, Object>> response =
          restTemplate.exchange(
              url,
              HttpMethod.POST,
              new HttpEntity<>(payload, headers),
              new ParameterizedTypeReference<Map<String, Object>>() {});
      Map<String, Object> bodyMap = response.getBody();
      result.put("httpStatus", response.getStatusCode().value());
      if (bodyMap != null) {
        result.put("notificationId", bodyMap.get("id"));
        result.put("status", bodyMap.get("status") != null ? bodyMap.get("status") : "QUEUED");
      } else {
        result.put("status", "QUEUED");
      }
      return result;
    } catch (RestClientException ex) {
      log.warn("notification-service queue failed tenant={}: {}", tenantId, ex.getMessage());
      result.put("status", "ERROR");
      result.put("error", ex.getMessage());
      if (properties.isFailOpen()) {
        result.put("failOpen", true);
        return result;
      }
      throw new NotificationDispatchException("Unable to queue notification", ex);
    }
  }

  private static String trimSlash(String base) {
    if (base == null || base.isBlank()) {
      return "http://localhost:8087";
    }
    return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
  }
}
