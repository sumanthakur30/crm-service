package com.shopmanagement.crmservice.web;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.shopmanagement.crmservice.entitlement.CrmEntitlementGuard;
import com.shopmanagement.crmservice.service.AccountContactService;

@RestController
@RequestMapping("/api/v1/crm")
public class AccountContactController {

  private final AccountContactService accountContactService;
  private final CrmEntitlementGuard entitlementGuard;

  public AccountContactController(
      AccountContactService accountContactService, CrmEntitlementGuard entitlementGuard) {
    this.accountContactService = accountContactService;
    this.entitlementGuard = entitlementGuard;
  }

  @GetMapping("/accounts")
  public List<Map<String, Object>> listAccounts() {
    entitlementGuard.requireCrmAccess();
    return accountContactService.listAccounts();
  }

  @GetMapping("/accounts/{id}")
  public Map<String, Object> getAccount(@PathVariable Long id) {
    entitlementGuard.requireCrmAccess();
    return accountContactService.getAccount(id);
  }

  @PostMapping("/accounts")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> upsertAccount(@RequestBody Map<String, Object> body) {
    entitlementGuard.requireCrmAccess();
    return accountContactService.upsertAccount(body);
  }

  @GetMapping("/contacts")
  public List<Map<String, Object>> listContacts(@RequestParam(required = false) Long accountId) {
    entitlementGuard.requireCrmAccess();
    return accountContactService.listContacts(accountId);
  }

  @GetMapping("/contacts/{id}")
  public Map<String, Object> getContact(@PathVariable Long id) {
    entitlementGuard.requireCrmAccess();
    return accountContactService.getContact(id);
  }

  @PostMapping("/contacts")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> upsertContact(@RequestBody Map<String, Object> body) {
    entitlementGuard.requireCrmAccess();
    return accountContactService.upsertContact(body);
  }
}
