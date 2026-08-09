package com.shopmanagement.crmservice.web;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shopmanagement.crmservice.entitlement.CrmEntitlementGuard;
import com.shopmanagement.crmservice.service.ActivityService;
import com.shopmanagement.crmservice.service.MyDayService;

@RestController
@RequestMapping("/api/v1/crm")
public class MyDayController {

  private final MyDayService myDayService;
  private final ActivityService activityService;
  private final CrmEntitlementGuard entitlementGuard;

  public MyDayController(
      MyDayService myDayService,
      ActivityService activityService,
      CrmEntitlementGuard entitlementGuard) {
    this.myDayService = myDayService;
    this.activityService = activityService;
    this.entitlementGuard = entitlementGuard;
  }

  @GetMapping("/my-day")
  public Map<String, Object> myDay(
      @RequestParam(required = false) Integer hotMinScore,
      @RequestParam(required = false) Integer hotLimit) {
    entitlementGuard.requireCrmAccess();
    return myDayService.snapshot(hotMinScore, hotLimit);
  }

  @GetMapping("/activities")
  public List<Map<String, Object>> activities(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
      @RequestParam(required = false) String types,
      @RequestParam(required = false, defaultValue = "100") int limit) {
    entitlementGuard.requireCrmAccess();
    return activityService.list(from, to, types, limit);
  }
}
