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

import com.shopmanagement.crmservice.api.CrmFormApi.WebFormResponse;
import com.shopmanagement.crmservice.api.CrmFormApi.WebFormUpsert;
import com.shopmanagement.crmservice.entitlement.CrmEntitlementGuard;
import com.shopmanagement.crmservice.service.WebFormService;

@RestController
@RequestMapping("/api/v1/crm/forms")
public class WebFormController {

  private final WebFormService webFormService;
  private final CrmEntitlementGuard entitlementGuard;

  public WebFormController(WebFormService webFormService, CrmEntitlementGuard entitlementGuard) {
    this.webFormService = webFormService;
    this.entitlementGuard = entitlementGuard;
  }

  @GetMapping
  public List<WebFormResponse> list() {
    entitlementGuard.requireCrmAccess();
    return webFormService.list();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public WebFormResponse create(@RequestBody WebFormUpsert body) {
    entitlementGuard.requireCrmAccess();
    return webFormService.create(body);
  }

  @PostMapping("/{id}/activate")
  public WebFormResponse activate(@PathVariable Long id) {
    entitlementGuard.requireCrmAccess();
    return webFormService.activate(id);
  }
}
