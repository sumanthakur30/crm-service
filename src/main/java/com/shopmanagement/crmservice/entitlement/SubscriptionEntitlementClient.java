package com.shopmanagement.crmservice.entitlement;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.shopmanagement.crmservice.config.CrmProperties;

@Component
public class SubscriptionEntitlementClient {

  private static final Logger log = LoggerFactory.getLogger(SubscriptionEntitlementClient.class);

  private final RestTemplate restTemplate;
  private final CrmProperties properties;

  public SubscriptionEntitlementClient(RestTemplate crmRestTemplate, CrmProperties properties) {
    this.restTemplate = crmRestTemplate;
    this.properties = properties;
  }

  /** Returns true when FEATURE_CRM is enabled for the tenant organization. */
  public boolean hasCrmFeature(String tenantId) {
    String url =
        UriComponentsBuilder.fromUriString(trimSlash(properties.getBaseUrl()))
            .path("/api/subscription/feature-flags/")
            .pathSegment(properties.getFlag())
            .toUriString();

    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Tenant-Id", tenantId);
    headers.set("X-Shop-Id", tenantId);
    headers.set("X-Gateway-Verified", "true");

    try {
      ResponseEntity<Map<String, Object>> response =
          restTemplate.exchange(
              url,
              HttpMethod.GET,
              new HttpEntity<>(headers),
              new ParameterizedTypeReference<Map<String, Object>>() {});
      Map<String, Object> body = response.getBody();
      if (body == null) {
        return false;
      }
      Object data = body.get("data");
      if (data instanceof Map<?, ?> dataMap) {
        return Boolean.TRUE.equals(dataMap.get("enabled"));
      }
      return Boolean.TRUE.equals(body.get("enabled"));
    } catch (RestClientException ex) {
      log.warn("CRM entitlement check failed for tenant={}: {}", tenantId, ex.getMessage());
      if (properties.isFailOpen()) {
        return true;
      }
      throw new CrmEntitlementException("Unable to verify CRM entitlement", ex);
    }
  }

  private static String trimSlash(String base) {
    if (base == null || base.isBlank()) {
      return "http://localhost:8182";
    }
    return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
  }
}
