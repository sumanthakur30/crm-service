package com.shopmanagement.crmservice.integration;

import java.util.LinkedHashMap;
import java.util.Map;

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

import com.shopmanagement.crmservice.config.CrmAiProperties;

/**
 * Optional LLM bridge. When {@code crm.ai.provider=HTTP} and {@code crm.ai.http-url} is set,
 * POSTs {@code {task, language, context}} and expects {@code {title, body, confidence?}}.
 */
@Component
public class AiHttpClient {

  private static final Logger log = LoggerFactory.getLogger(AiHttpClient.class);

  private final RestTemplate restTemplate;
  private final CrmAiProperties properties;

  public AiHttpClient(RestTemplate crmRestTemplate, CrmAiProperties properties) {
    this.restTemplate = crmRestTemplate;
    this.properties = properties;
  }

  public boolean isHttpProvider() {
    return "HTTP".equalsIgnoreCase(properties.getProvider())
        && properties.getHttpUrl() != null
        && !properties.getHttpUrl().isBlank();
  }

  public Map<String, Object> complete(String task, String language, Map<String, Object> context) {
    if (!isHttpProvider()) {
      return Map.of();
    }
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("task", task);
    payload.put("language", language);
    payload.put("context", context == null ? Map.of() : context);
    payload.put("modelCode", properties.getModelCode());

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    try {
      ResponseEntity<Map<String, Object>> response =
          restTemplate.exchange(
              properties.getHttpUrl().trim(),
              HttpMethod.POST,
              new HttpEntity<>(payload, headers),
              new ParameterizedTypeReference<Map<String, Object>>() {});
      return response.getBody() == null ? Map.of() : response.getBody();
    } catch (RestClientException ex) {
      log.warn("AI HTTP bridge failed: {}", ex.getMessage());
      return Map.of("error", ex.getMessage() == null ? "ai http failed" : ex.getMessage());
    }
  }
}
