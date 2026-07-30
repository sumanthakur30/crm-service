package com.shopmanagement.crmservice.filter;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Binds tenant from gateway header {@code X-Tenant-Id} (string org/shop id). Skips actuator/docs.
 */
@Component
public class TenantContextFilter extends OncePerRequestFilter {

  public static final String REQUEST_ID_HEADER = "X-Request-Id";
  public static final String TENANT_ID_HEADER = "X-Tenant-Id";
  public static final String SHOP_ID_HEADER = "X-Shop-Id";
  public static final String USER_ID_HEADER = "X-User-Id";
  public static final String REQUEST_ID_MDC_KEY = "requestId";

  private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();
  private static final ThreadLocal<String> CURRENT_SHOP = new ThreadLocal<>();
  private static final ThreadLocal<String> CURRENT_USER = new ThreadLocal<>();

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String requestId = request.getHeader(REQUEST_ID_HEADER);
    if (requestId == null || requestId.isBlank()) {
      requestId = UUID.randomUUID().toString();
    }
    MDC.put(REQUEST_ID_MDC_KEY, requestId);
    response.setHeader(REQUEST_ID_HEADER, requestId);

    try {
      if (!"OPTIONS".equalsIgnoreCase(request.getMethod()) && !skipsTenant(request.getRequestURI())) {
        String tenantId = request.getHeader(TENANT_ID_HEADER);
        if (tenantId == null || tenantId.isBlank()) {
          response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
          response.setContentType("application/json");
          response.getWriter().write("{\"message\":\"Missing tenant context header: X-Tenant-Id\"}");
          return;
        }
        CURRENT_TENANT.set(tenantId.trim());
        String shopId = request.getHeader(SHOP_ID_HEADER);
        if (shopId != null && !shopId.isBlank()) {
          CURRENT_SHOP.set(shopId.trim());
        }
        String userId = request.getHeader(USER_ID_HEADER);
        if (userId != null && !userId.isBlank()) {
          CURRENT_USER.set(userId.trim());
        }
      }
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(REQUEST_ID_MDC_KEY);
      CURRENT_TENANT.remove();
      CURRENT_SHOP.remove();
      CURRENT_USER.remove();
    }
  }

  public static String getCurrentTenantId() {
    return CURRENT_TENANT.get();
  }

  public static String getCurrentShopId() {
    return CURRENT_SHOP.get();
  }

  public static String getCurrentUserId() {
    return CURRENT_USER.get();
  }

  /** Test hook — do not use in production request paths. */
  public static void bindTenantForTests(String tenantId) {
    CURRENT_TENANT.set(tenantId);
  }

  /** Test hook — do not use in production request paths. */
  public static void bindShopForTests(String shopId) {
    CURRENT_SHOP.set(shopId);
  }

  /** Test hook — do not use in production request paths. */
  public static void clearTenantForTests() {
    CURRENT_TENANT.remove();
    CURRENT_SHOP.remove();
    CURRENT_USER.remove();
  }

  private static boolean skipsTenant(String uri) {
    return uri.startsWith("/actuator")
        || uri.startsWith("/swagger-ui")
        || uri.startsWith("/v3/api-docs")
        || uri.startsWith("/webjars")
        || uri.equals("/api/v1/crm/status")
        || uri.startsWith("/api/v1/crm/public/");
  }

  /** Bind tenant for public capture after resolving campaign. */
  public static void bindTenant(String tenantId) {
    CURRENT_TENANT.set(tenantId);
  }
}
