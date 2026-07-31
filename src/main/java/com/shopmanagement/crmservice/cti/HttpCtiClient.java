package com.shopmanagement.crmservice.cti;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.shopmanagement.crmservice.config.CrmCtiProperties;

/**
 * HTTP CTI provider — POSTs click-to-dial payload to {@code crm.cti.webhook-url}
 * (Twilio Function / Exotel webhook / custom softphone bridge).
 */
@Component
@ConditionalOnProperty(name = "crm.cti.provider", havingValue = "HTTP")
public class HttpCtiClient implements CtiClient {

  private static final Logger log = LoggerFactory.getLogger(HttpCtiClient.class);

  private final RestTemplate restTemplate;
  private final CrmCtiProperties properties;

  public HttpCtiClient(RestTemplate crmRestTemplate, CrmCtiProperties properties) {
    this.restTemplate = crmRestTemplate;
    this.properties = properties;
  }

  @Override
  public Map<String, Object> clickToDial(String tenantId, String phone, Map<String, Object> context) {
    String callId = "http-" + UUID.randomUUID();
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("event", "CLICK_TO_DIAL");
    payload.put("tenantId", tenantId);
    payload.put("phone", phone);
    payload.put("callId", callId);
    payload.put("context", context == null ? Map.of() : context);

    String url = properties.getWebhookUrl() == null ? "" : properties.getWebhookUrl().trim();
    if (url.isBlank()) {
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("status", "MISCONFIGURED");
      out.put("provider", "HTTP");
      out.put("callId", callId);
      out.put("phone", phone);
      out.put("message", "crm.cti.webhook-url is empty");
      return out;
    }

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("X-Tenant-Id", tenantId);
    try {
      ResponseEntity<Map<String, Object>> response =
          restTemplate.exchange(
              url,
              HttpMethod.POST,
              new HttpEntity<>(payload, headers),
              new ParameterizedTypeReference<Map<String, Object>>() {});
      Map<String, Object> body = response.getBody() == null ? Map.of() : response.getBody();
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("status", "DISPATCHED");
      out.put("provider", "HTTP");
      out.put("callId", callId);
      out.put("phone", phone);
      out.put("httpStatus", response.getStatusCode().value());
      out.put("response", body);
      return out;
    } catch (RestClientException ex) {
      log.warn("CTI HTTP click-to-dial failed tenant={} phone={}: {}", tenantId, phone, ex.getMessage());
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("status", "ERROR");
      out.put("provider", "HTTP");
      out.put("callId", callId);
      out.put("phone", phone);
      out.put("error", ex.getMessage());
      return out;
    }
  }
}
