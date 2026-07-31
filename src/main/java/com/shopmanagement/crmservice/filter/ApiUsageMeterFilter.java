package com.shopmanagement.crmservice.filter;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopmanagement.crmservice.meter.CrmMeterExceededException;
import com.shopmanagement.crmservice.service.UsageMeterService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Increments API_CALLS_MONTH for authenticated CRM API traffic. Enforces plan limit only when
 * entitlement checks are enabled (fail-open mirrors subscription client).
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 20)
public class ApiUsageMeterFilter extends OncePerRequestFilter {

  private final UsageMeterService usageMeterService;
  private final ObjectMapper objectMapper;

  public ApiUsageMeterFilter(UsageMeterService usageMeterService, ObjectMapper objectMapper) {
    this.usageMeterService = usageMeterService;
    this.objectMapper = objectMapper;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
      return true;
    }
    String uri = request.getRequestURI();
    if (uri == null || !uri.startsWith("/api/v1/crm/")) {
      return true;
    }
    return uri.equals("/api/v1/crm/status")
        || uri.equals("/api/v1/crm/entitlements")
        || uri.startsWith("/api/v1/crm/public/")
        || uri.equals("/api/v1/crm/sso/callback")
        || uri.startsWith("/api/v1/crm/meters");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String tenantId = request.getHeader(TenantContextFilter.TENANT_ID_HEADER);
    if (tenantId != null && !tenantId.isBlank()) {
      try {
        usageMeterService.incrementApiCall(tenantId.trim());
      } catch (CrmMeterExceededException ex) {
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
            response.getWriter(),
            java.util.Map.of("message", ex.getMessage(), "code", ex.getCode()));
        return;
      }
    }
    filterChain.doFilter(request, response);
  }
}
