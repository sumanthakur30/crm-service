package com.shopmanagement.crmservice.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shopmanagement.crmservice.entitlement.CrmEntitlementGuard;
import com.shopmanagement.crmservice.persistence.entity.CrmTerritoryEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmTerritoryRepository;
import com.shopmanagement.crmservice.support.TenantIds;

/** Sprint 10 — light labs read APIs (territory scaffold only). */
@RestController
@RequestMapping("/api/v1/crm/labs")
public class LabsController {

  private final CrmTerritoryRepository territoryRepository;
  private final CrmEntitlementGuard entitlementGuard;

  public LabsController(
      CrmTerritoryRepository territoryRepository, CrmEntitlementGuard entitlementGuard) {
    this.territoryRepository = territoryRepository;
    this.entitlementGuard = entitlementGuard;
  }

  @GetMapping("/territories")
  public List<Map<String, Object>> territories() {
    entitlementGuard.requireCrmAccess();
    return territoryRepository
        .findByTenantIdAndDeletedAtIsNullOrderByCodeAsc(TenantIds.require())
        .stream()
        .map(LabsController::toTerritory)
        .toList();
  }

  private static Map<String, Object> toTerritory(CrmTerritoryEntity t) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", t.getId());
    m.put("code", t.getCode());
    m.put("name", t.getName());
    m.put("regionCode", t.getRegionCode());
    return m;
  }
}
