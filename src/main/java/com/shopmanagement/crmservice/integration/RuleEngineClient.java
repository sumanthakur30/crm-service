package com.shopmanagement.crmservice.integration;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
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

import com.shopmanagement.crmservice.config.CrmRuleEngineProperties;

/**
 * Optional bridge to School {@code rule-engine-service} {@code POST /api/rules/evaluate}.
 * Fail-open by default so CRM stage moves never block on School outages.
 */
@Component
public class RuleEngineClient {

  private static final Logger log = LoggerFactory.getLogger(RuleEngineClient.class);

  private final RestTemplate restTemplate;
  private final CrmRuleEngineProperties properties;

  public RuleEngineClient(RestTemplate crmRestTemplate, CrmRuleEngineProperties properties) {
    this.restTemplate = crmRestTemplate;
    this.properties = properties;
  }

  public boolean isEnabled() {
    return properties.isEnabled();
  }

  @SuppressWarnings("unchecked")
  public List<String> evaluateMatchedActions(String tenantId, Map<String, Object> context) {
    if (!properties.isEnabled()) {
      return List.of();
    }
    String url = trimSlash(properties.getBaseUrl()) + "/api/rules/evaluate";
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("X-Tenant-Id", tenantId);
    headers.set("X-Organization-Id", tenantId);
    try {
      ResponseEntity<Map<String, Object>> response =
          restTemplate.exchange(
              url,
              HttpMethod.POST,
              new HttpEntity<>(context, headers),
              new ParameterizedTypeReference<>() {});
      Map<String, Object> body = response.getBody();
      if (body == null) {
        return List.of();
      }
      Object data = body.get("data");
      Map<String, Object> payload =
          data instanceof Map<?, ?> m ? (Map<String, Object>) m : body;
      Object matched = payload.get("matchedActions");
      if (matched instanceof List<?> list) {
        return list.stream().map(String::valueOf).toList();
      }
      return List.of();
    } catch (RestClientException ex) {
      log.warn("rule-engine evaluate failed for tenant {}: {}", tenantId, ex.getMessage());
      if (!properties.isFailOpen()) {
        throw ex;
      }
      return Collections.emptyList();
    }
  }

  private static String trimSlash(String base) {
    if (base == null || base.isBlank()) {
      return "";
    }
    return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
  }
}
