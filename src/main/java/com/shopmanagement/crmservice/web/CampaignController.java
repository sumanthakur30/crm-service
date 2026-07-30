package com.shopmanagement.crmservice.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.shopmanagement.crmservice.api.CrmCampaignApi.CampaignResponse;
import com.shopmanagement.crmservice.api.CrmCampaignApi.CampaignUpsert;
import com.shopmanagement.crmservice.api.CrmCampaignApi.PublicCaptureRequest;
import com.shopmanagement.crmservice.api.CrmLeadApi.LeadResponse;
import com.shopmanagement.crmservice.entitlement.CrmEntitlementGuard;
import com.shopmanagement.crmservice.service.CampaignService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/crm")
public class CampaignController {

  private final CampaignService campaignService;
  private final CrmEntitlementGuard entitlementGuard;

  public CampaignController(CampaignService campaignService, CrmEntitlementGuard entitlementGuard) {
    this.campaignService = campaignService;
    this.entitlementGuard = entitlementGuard;
  }

  @GetMapping("/campaigns")
  public List<CampaignResponse> list() {
    entitlementGuard.requireCrmAccess();
    return campaignService.list();
  }

  @GetMapping("/campaigns/{id}")
  public CampaignResponse get(@PathVariable Long id) {
    entitlementGuard.requireCrmAccess();
    return campaignService.get(id);
  }

  @PostMapping("/campaigns")
  @ResponseStatus(HttpStatus.CREATED)
  public CampaignResponse upsert(@Valid @RequestBody CampaignUpsert body) {
    entitlementGuard.requireCrmAccess();
    return campaignService.upsert(body);
  }

  /** Public web/form capture — no X-Tenant-Id; resolves tenant from campaign public key. */
  @PostMapping("/public/capture/{publicKey}")
  @ResponseStatus(HttpStatus.CREATED)
  public LeadResponse capturePublic(
      @PathVariable String publicKey, @Valid @RequestBody PublicCaptureRequest body) {
    return campaignService.capturePublic(publicKey, body);
  }
}
