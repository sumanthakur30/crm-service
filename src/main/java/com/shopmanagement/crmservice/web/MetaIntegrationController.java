package com.shopmanagement.crmservice.web;

import java.io.IOException;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.ContentCachingRequestWrapper;

import com.shopmanagement.crmservice.config.CrmMetaProperties;
import com.shopmanagement.crmservice.entitlement.CrmEntitlementGuard;
import com.shopmanagement.crmservice.integration.meta.MetaConnectionService;
import com.shopmanagement.crmservice.integration.meta.MetaConnectionService.ConnectLink;
import com.shopmanagement.crmservice.integration.meta.MetaConnectionService.Desk;
import com.shopmanagement.crmservice.integration.meta.MetaConnectionService.Selection;
import com.shopmanagement.crmservice.integration.meta.MetaWebhookService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/v1/crm")
public class MetaIntegrationController {

  private final MetaConnectionService connectionService;
  private final MetaWebhookService webhookService;
  private final CrmEntitlementGuard entitlementGuard;
  private final CrmMetaProperties properties;

  public MetaIntegrationController(
      MetaConnectionService connectionService,
      MetaWebhookService webhookService,
      CrmEntitlementGuard entitlementGuard,
      CrmMetaProperties properties) {
    this.connectionService = connectionService;
    this.webhookService = webhookService;
    this.entitlementGuard = entitlementGuard;
    this.properties = properties;
  }

  @GetMapping("/integrations/meta/connect")
  public ConnectLink connect() {
    entitlementGuard.requireCrmAccess();
    return connectionService.connectLink();
  }

  @GetMapping("/integrations/meta")
  public Desk desk() {
    entitlementGuard.requireCrmAccess();
    return connectionService.desk();
  }

  @PutMapping("/integrations/meta/selection")
  public Desk save(@RequestBody Selection body) {
    entitlementGuard.requireCrmAccess();
    return connectionService.saveSelection(body);
  }

  @GetMapping("/public/integrations/meta/callback")
  public void callback(
      @RequestParam(required = false) String code,
      @RequestParam(required = false) String state,
      @RequestParam(name = "error", required = false) String oauthError,
      HttpServletResponse response)
      throws IOException {
    String target = returnTarget(code, state, oauthError);
    response.sendRedirect(target);
  }

  @GetMapping(value = "/public/webhooks/meta", produces = MediaType.TEXT_PLAIN_VALUE)
  public String verify(
      @RequestParam("hub.mode") String mode,
      @RequestParam("hub.verify_token") String verifyToken,
      @RequestParam("hub.challenge") String challenge) {
    return webhookService.challenge(mode, verifyToken, challenge);
  }

  @PostMapping("/public/webhooks/meta")
  public Map<String, Object> receive(
      @RequestBody Map<String, Object> payload,
      @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
      HttpServletRequest request) {
    return webhookService.receive(rawBody(request), signature, payload);
  }

  private String returnTarget(String code, String state, String oauthError) {
    if (oauthError != null && !oauthError.isBlank()) {
      return errorReturn();
    }
    try {
      return connectionService.finishCallback(code, state);
    } catch (RuntimeException ex) {
      return errorReturn();
    }
  }

  private String errorReturn() {
    String base = properties.getUiReturnUrl() == null ? "http://localhost:4500/integrations" : properties.getUiReturnUrl();
    return base + (base.contains("?") ? "&" : "?") + "meta=error";
  }

  private static byte[] rawBody(HttpServletRequest request) {
    if (request instanceof ContentCachingRequestWrapper wrapper) {
      byte[] cached = wrapper.getContentAsByteArray();
      return cached == null ? new byte[0] : cached;
    }
    return new byte[0];
  }
}
