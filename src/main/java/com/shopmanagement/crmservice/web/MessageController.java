package com.shopmanagement.crmservice.web;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.shopmanagement.crmservice.api.CrmMessageApi.SendRequest;
import com.shopmanagement.crmservice.entitlement.CrmEntitlementGuard;
import com.shopmanagement.crmservice.service.MessageService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/crm/messages")
public class MessageController {

  private final MessageService messageService;
  private final CrmEntitlementGuard entitlementGuard;

  public MessageController(MessageService messageService, CrmEntitlementGuard entitlementGuard) {
    this.messageService = messageService;
    this.entitlementGuard = entitlementGuard;
  }

  @PostMapping("/send")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> send(@Valid @RequestBody SendRequest body) {
    entitlementGuard.requireCrmAccess();
    if (body != null) {
      entitlementGuard.requireChannelAccess(body.channel());
    }
    return messageService.send(body);
  }
}
