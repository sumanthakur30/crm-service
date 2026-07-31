package com.shopmanagement.crmservice.web;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shopmanagement.crmservice.entitlement.CrmEntitlementGuard;
import com.shopmanagement.crmservice.service.UsageMeterService;

@RestController
@RequestMapping("/api/v1/crm/meters")
public class MeterController {

  private final UsageMeterService usageMeterService;
  private final CrmEntitlementGuard entitlementGuard;

  public MeterController(UsageMeterService usageMeterService, CrmEntitlementGuard entitlementGuard) {
    this.usageMeterService = usageMeterService;
    this.entitlementGuard = entitlementGuard;
  }

  @GetMapping
  public Map<String, Object> meters() {
    entitlementGuard.requireCrmAccess();
    return usageMeterService.snapshot();
  }

  @GetMapping("/seats")
  public Map<String, Object> seats() {
    entitlementGuard.requireCrmAccess();
    Map<String, Object> snap = usageMeterService.snapshot();
    @SuppressWarnings("unchecked")
    Map<String, Object> seats = (Map<String, Object>) snap.get("seats");
    return seats;
  }

  @PutMapping("/seats")
  public Map<String, Object> setSeats(@RequestBody Map<String, Object> body) {
    entitlementGuard.requireCrmAccess();
    if (body.get("used") == null && body.get("usedCount") == null) {
      throw new IllegalStateException("used required");
    }
    Object raw = body.get("used") != null ? body.get("used") : body.get("usedCount");
    long used = ((Number) raw).longValue();
    return usageMeterService.setSeats(used);
  }
}
