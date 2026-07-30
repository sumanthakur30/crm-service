package com.shopmanagement.crmservice.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.shopmanagement.crmservice.persistence.entity.CrmObjectTagEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmTagEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmObjectTagRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmTagRepository;
import com.shopmanagement.crmservice.support.TenantIds;

@Service
public class TagService {

  private static final Set<String> OBJECT_TYPES = Set.of("LEAD", "OPPORTUNITY", "ACCOUNT", "QUOTATION");

  private final CrmTagRepository tagRepository;
  private final CrmObjectTagRepository objectTagRepository;

  public TagService(CrmTagRepository tagRepository, CrmObjectTagRepository objectTagRepository) {
    this.tagRepository = tagRepository;
    this.objectTagRepository = objectTagRepository;
  }

  @Transactional
  public List<Map<String, Object>> listTags() {
    String tenantId = TenantIds.require();
    ensureDefaults(tenantId);
    return tagRepository.findByTenantIdAndDeletedAtIsNullOrderByNameAsc(tenantId).stream()
        .map(TagService::toMap)
        .toList();
  }

  @Transactional
  public Map<String, Object> createTag(Map<String, Object> body) {
    String tenantId = TenantIds.require();
    String code = req(body, "code").toUpperCase(Locale.ROOT);
    if (tagRepository.findByTenantIdAndCodeAndDeletedAtIsNull(tenantId, code).isPresent()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Tag code already exists");
    }
    CrmTagEntity t = new CrmTagEntity();
    t.setTenantId(tenantId);
    t.setCode(code);
    t.setName(req(body, "name"));
    t.setColor(blankToNull(str(body.get("color"))));
    return toMap(tagRepository.save(t));
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listForObject(String objectType, Long objectId) {
    String tenantId = TenantIds.require();
    String type = normalizeType(objectType);
    return objectTagRepository.findByTenantIdAndObjectTypeAndObjectId(tenantId, type, objectId).stream()
        .map(CrmObjectTagEntity::getTagId)
        .map(id -> tagRepository.findByTenantIdAndIdAndDeletedAtIsNull(tenantId, id).orElse(null))
        .filter(t -> t != null)
        .map(TagService::toMap)
        .toList();
  }

  @Transactional
  public List<Map<String, Object>> assign(String objectType, Long objectId, Long tagId) {
    String tenantId = TenantIds.require();
    String type = normalizeType(objectType);
    tagRepository
        .findByTenantIdAndIdAndDeletedAtIsNull(tenantId, tagId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown tagId"));
    if (objectTagRepository
        .findByTenantIdAndObjectTypeAndObjectIdAndTagId(tenantId, type, objectId, tagId)
        .isEmpty()) {
      CrmObjectTagEntity link = new CrmObjectTagEntity();
      link.setTenantId(tenantId);
      link.setObjectType(type);
      link.setObjectId(objectId);
      link.setTagId(tagId);
      objectTagRepository.save(link);
    }
    return listForObject(type, objectId);
  }

  @Transactional
  public List<Map<String, Object>> remove(String objectType, Long objectId, Long tagId) {
    String tenantId = TenantIds.require();
    String type = normalizeType(objectType);
    objectTagRepository.deleteByTenantIdAndObjectTypeAndObjectIdAndTagId(tenantId, type, objectId, tagId);
    return listForObject(type, objectId);
  }

  private void ensureDefaults(String tenantId) {
    if (!tagRepository.findByTenantIdAndDeletedAtIsNullOrderByNameAsc(tenantId).isEmpty()) {
      return;
    }
    seed(tenantId, "HOT", "Hot", "#c0392b");
    seed(tenantId, "VIP", "VIP", "#8e44ad");
    seed(tenantId, "FOLLOW_UP", "Follow-up", "#2980b9");
  }

  private void seed(String tenantId, String code, String name, String color) {
    CrmTagEntity t = new CrmTagEntity();
    t.setTenantId(tenantId);
    t.setCode(code);
    t.setName(name);
    t.setColor(color);
    tagRepository.save(t);
  }

  private static String normalizeType(String objectType) {
    String type = objectType == null ? "" : objectType.trim().toUpperCase(Locale.ROOT);
    if (!OBJECT_TYPES.contains(type)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid objectType");
    }
    return type;
  }

  private static Map<String, Object> toMap(CrmTagEntity t) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", t.getId());
    m.put("code", t.getCode());
    m.put("name", t.getName());
    m.put("color", t.getColor());
    return m;
  }

  private static String req(Map<String, Object> body, String key) {
    String v = str(body.get(key));
    if (v == null || v.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " is required");
    }
    return v.trim();
  }

  private static String str(Object v) {
    return v == null ? null : String.valueOf(v);
  }

  private static String blankToNull(String v) {
    return v == null || v.isBlank() ? null : v.trim();
  }
}
