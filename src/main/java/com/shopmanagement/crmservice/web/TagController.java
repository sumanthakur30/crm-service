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
import com.shopmanagement.crmservice.service.TagService;

@RestController
@RequestMapping("/api/v1/crm/tags")
public class TagController {

  private final TagService tagService;
  private final CrmEntitlementGuard entitlementGuard;

  public TagController(TagService tagService, CrmEntitlementGuard entitlementGuard) {
    this.tagService = tagService;
    this.entitlementGuard = entitlementGuard;
  }

  @GetMapping
  public List<Map<String, Object>> list() {
    entitlementGuard.requireCrmAccess();
    return tagService.listTags();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> create(@RequestBody Map<String, Object> body) {
    entitlementGuard.requireCrmAccess();
    return tagService.createTag(body);
  }

  @GetMapping("/assignments/{objectType}/{objectId}")
  public List<Map<String, Object>> assigned(
      @PathVariable String objectType, @PathVariable Long objectId) {
    entitlementGuard.requireCrmAccess();
    return tagService.listForObject(objectType, objectId);
  }

  @PostMapping("/assignments/{objectType}/{objectId}")
  public List<Map<String, Object>> assign(
      @PathVariable String objectType,
      @PathVariable Long objectId,
      @RequestBody Map<String, Object> body) {
    entitlementGuard.requireCrmAccess();
    Long tagId =
        body.get("tagId") instanceof Number n
            ? n.longValue()
            : Long.parseLong(String.valueOf(body.get("tagId")));
    return tagService.assign(objectType, objectId, tagId);
  }

  @DeleteMapping("/assignments/{objectType}/{objectId}/{tagId}")
  public List<Map<String, Object>> remove(
      @PathVariable String objectType, @PathVariable Long objectId, @PathVariable Long tagId) {
    entitlementGuard.requireCrmAccess();
    return tagService.remove(objectType, objectId, tagId);
  }
}
