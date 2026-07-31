package com.shopmanagement.crmservice.integration;

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

import com.shopmanagement.crmservice.config.CrmOrderProperties;
import com.shopmanagement.crmservice.filter.TenantContextFilter;

@Component
public class OrderClient {

  private static final Logger log = LoggerFactory.getLogger(OrderClient.class);

  private final RestTemplate restTemplate;
  private final CrmOrderProperties properties;

  public OrderClient(RestTemplate crmRestTemplate, CrmOrderProperties properties) {
    this.restTemplate = crmRestTemplate;
    this.properties = properties;
  }

  public boolean isEnabled() {
    return properties.isEnabled();
  }

  public CrmOrderProperties properties() {
    return properties;
  }

  /**
   * Idempotent order create via order-service {@code POST /api/v1/orders/sync}.
   *
   * @return map with status + orderId (or error / skipped)
   */
  public Map<String, Object> createFromQuote(
      String tenantId, String idempotencyKey, Map<String, Object> orderPayload) {
    Map<String, Object> result = new LinkedHashMap<>();
    if (!properties.isEnabled()) {
      result.put("status", "SKIPPED_DISABLED");
      return result;
    }
    String shopId = resolveShopId(tenantId);
    String platformTenantId = resolvePlatformTenantId(shopId);
    // order-service mounts at /orders (gateway may expose /api/v1/orders)
    String url = trimSlash(properties.getBaseUrl()) + "/orders/sync";

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("X-Tenant-Id", platformTenantId);
    headers.set("X-Shop-Id", shopId);
    headers.set("X-Idempotency-Key", idempotencyKey);
    // Service-to-service: mirror gateway-trusted permission headers for @RequiresModule / filters
    headers.set("X-Auth-Permissions", "MANAGE_ORDERS,MANAGE_STOCKS");
    headers.set("X-Auth-User", "crm-service");
    headers.set("X-Auth-Role", "SERVICE");
    if (properties.getInternalApiKey() != null && !properties.getInternalApiKey().isBlank()) {
      headers.set("X-Internal-Api-Key", properties.getInternalApiKey().trim());
    }

    try {
      ResponseEntity<Map<String, Object>> response =
          restTemplate.exchange(
              url,
              HttpMethod.POST,
              new HttpEntity<>(orderPayload, headers),
              new ParameterizedTypeReference<Map<String, Object>>() {});
      Map<String, Object> body = response.getBody() == null ? Map.of() : response.getBody();
      result.put("status", "CREATED");
      result.put("httpStatus", response.getStatusCode().value());
      result.put("orderId", body.get("id"));
      result.put("orderNumber", body.get("orderNumber"));
      result.put("response", body);
      return result;
    } catch (RestClientException ex) {
      log.warn("order-service create failed tenant={}: {}", tenantId, ex.getMessage());
      result.put("status", "ERROR");
      result.put("error", ex.getMessage());
      if (properties.isFailOpen()) {
        result.put("failOpen", true);
        return result;
      }
      throw new OrderDispatchException("Unable to create order from quote", ex);
    }
  }

  public Map<String, Object> buildOrderPayload(
      Long customerId, Long productId, List<Map<String, Object>> items, Map<String, Object> totals) {
    Map<String, Object> order = new LinkedHashMap<>();
    order.put("customerId", customerId);
    order.put("branchId", 1L);
    order.put("orderChannel", "CRM_QUOTE");
    order.put("billType", "SALE");
    order.put("status", "PENDING");
    order.put("paymentStatus", "UNPAID");
    order.put("items", items);
    if (totals != null) {
      order.putAll(totals);
    }
    if (productId != null && items != null) {
      for (Map<String, Object> item : items) {
        item.putIfAbsent("productId", productId);
      }
    }
    return order;
  }

  private String resolveShopId(String tenantId) {
    if (properties.getShopId() != null && !properties.getShopId().isBlank()) {
      return properties.getShopId().trim();
    }
    String fromRequest = TenantContextFilter.getCurrentShopId();
    if (fromRequest != null && !fromRequest.isBlank()) {
      return fromRequest.trim();
    }
    return tenantId;
  }

  private String resolvePlatformTenantId(String shopId) {
    if (properties.getTenantId() != null && !properties.getTenantId().isBlank()) {
      return properties.getTenantId().trim();
    }
    // When shopId is already numeric (legacy CRM_ORDER_SHOP_ID=102), reuse it as tenant.
    if (shopId != null && shopId.chars().allMatch(Character::isDigit)) {
      return shopId;
    }
    return shopId;
  }

  private static String trimSlash(String base) {
    if (base == null || base.isBlank()) {
      return "http://localhost:8083";
    }
    return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
  }
}
