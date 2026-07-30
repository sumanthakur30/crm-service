package com.shopmanagement.crmservice.web;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.shopmanagement.crmservice.entitlement.CrmEntitlementGuard;
import com.shopmanagement.crmservice.service.InboundAdapterService;

@RestController
@RequestMapping("/api/v1/crm")
public class InboundAdapterController {

  private final InboundAdapterService inboundAdapterService;
  private final CrmEntitlementGuard entitlementGuard;

  public InboundAdapterController(
      InboundAdapterService inboundAdapterService, CrmEntitlementGuard entitlementGuard) {
    this.inboundAdapterService = inboundAdapterService;
    this.entitlementGuard = entitlementGuard;
  }

  /** Public webhooks — resolve tenant via X-Tenant-Id or campaign publicKey in body/query. */
  @PostMapping("/public/adapters/{provider}")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> publicIngest(
      @PathVariable String provider,
      @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
      @RequestParam(required = false) String publicKey,
      @RequestBody Map<String, Object> payload) {
    String key =
        publicKey != null
            ? publicKey
            : payload.get("publicKey") == null ? null : String.valueOf(payload.get("publicKey"));
    return inboundAdapterService.ingest(provider, tenantId, key, payload);
  }

  @GetMapping("/adapters/events")
  public List<Map<String, Object>> recent(@RequestParam(defaultValue = "50") int limit) {
    entitlementGuard.requireCrmAccess();
    return inboundAdapterService.recent(limit);
  }

  @PostMapping("/adapters/{provider}")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> authenticatedIngest(
      @PathVariable String provider, @RequestBody Map<String, Object> payload) {
    entitlementGuard.requireApiAccess();
    String key = payload.get("publicKey") == null ? null : String.valueOf(payload.get("publicKey"));
    return inboundAdapterService.ingest(
        provider, com.shopmanagement.crmservice.support.TenantIds.require(), key, payload);
  }
}
