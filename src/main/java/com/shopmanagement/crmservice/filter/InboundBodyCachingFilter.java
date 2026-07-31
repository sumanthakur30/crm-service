package com.shopmanagement.crmservice.filter;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Caches raw request body for public adapter webhooks so HMAC can be verified over the exact
 * bytes received (after Spring has consumed the input stream for {@code @RequestBody}).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class InboundBodyCachingFilter extends OncePerRequestFilter {

  static final String ADAPTER_PREFIX = "/api/v1/crm/public/adapters/";

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String uri = request.getRequestURI();
    return uri == null || !uri.startsWith(ADAPTER_PREFIX);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    ContentCachingRequestWrapper wrapped =
        request instanceof ContentCachingRequestWrapper c
            ? c
            : new ContentCachingRequestWrapper(request);
    filterChain.doFilter(wrapped, response);
  }
}
