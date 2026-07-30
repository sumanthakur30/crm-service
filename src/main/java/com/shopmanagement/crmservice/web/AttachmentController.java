package com.shopmanagement.crmservice.web;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.shopmanagement.crmservice.entitlement.CrmEntitlementGuard;
import com.shopmanagement.crmservice.service.AttachmentService;

@RestController
@RequestMapping("/api/v1/crm/attachments")
public class AttachmentController {

  private final AttachmentService attachmentService;
  private final CrmEntitlementGuard entitlementGuard;

  public AttachmentController(AttachmentService attachmentService, CrmEntitlementGuard entitlementGuard) {
    this.attachmentService = attachmentService;
    this.entitlementGuard = entitlementGuard;
  }

  @GetMapping("/{objectType}/{objectId}")
  public List<Map<String, Object>> list(@PathVariable String objectType, @PathVariable Long objectId) {
    entitlementGuard.requireCrmAccess();
    return attachmentService.list(objectType, objectId);
  }

  @PostMapping("/{objectType}/{objectId}")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> create(
      @PathVariable String objectType,
      @PathVariable Long objectId,
      @RequestBody Map<String, Object> body) {
    entitlementGuard.requireCrmAccess();
    return attachmentService.create(objectType, objectId, body);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id) {
    entitlementGuard.requireCrmAccess();
    attachmentService.delete(id);
  }
}
