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

import com.shopmanagement.crmservice.persistence.entity.CrmAttachmentEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmAttachmentRepository;
import com.shopmanagement.crmservice.support.TenantIds;

@Service
public class AttachmentService {

  private static final Set<String> OBJECT_TYPES = Set.of("LEAD", "OPPORTUNITY", "ACCOUNT", "QUOTATION");

  private final CrmAttachmentRepository attachmentRepository;

  public AttachmentService(CrmAttachmentRepository attachmentRepository) {
    this.attachmentRepository = attachmentRepository;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> list(String objectType, Long objectId) {
    String tenantId = TenantIds.require();
    String type = normalizeType(objectType);
    return attachmentRepository
        .findByTenantIdAndObjectTypeAndObjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(tenantId, type, objectId)
        .stream()
        .map(AttachmentService::toMap)
        .toList();
  }

  @Transactional
  public Map<String, Object> create(String objectType, Long objectId, Map<String, Object> body) {
    String tenantId = TenantIds.require();
    String type = normalizeType(objectType);
    String fileName = req(body, "fileName");
    CrmAttachmentEntity a = new CrmAttachmentEntity();
    a.setTenantId(tenantId);
    a.setObjectType(type);
    a.setObjectId(objectId);
    a.setFileName(fileName);
    a.setContentType(blankToNull(str(body.get("contentType"))));
    a.setStorageUrl(blankToNull(str(body.get("storageUrl"))));
    a.setNote(blankToNull(str(body.get("note"))));
    if (body.get("sizeBytes") instanceof Number n) {
      a.setSizeBytes(n.longValue());
    }
    if (a.getStorageUrl() == null && a.getNote() != null) {
      a.setStorageUrl("note://" + System.currentTimeMillis());
      a.setContentType(a.getContentType() == null ? "text/plain" : a.getContentType());
    }
    return toMap(attachmentRepository.save(a));
  }

  @Transactional
  public void delete(Long id) {
    String tenantId = TenantIds.require();
    CrmAttachmentEntity a =
        attachmentRepository
            .findByTenantIdAndIdAndDeletedAtIsNull(tenantId, id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment not found"));
    a.softDelete();
    attachmentRepository.save(a);
  }

  private static String normalizeType(String objectType) {
    String type = objectType == null ? "" : objectType.trim().toUpperCase(Locale.ROOT);
    if (!OBJECT_TYPES.contains(type)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid objectType");
    }
    return type;
  }

  private static Map<String, Object> toMap(CrmAttachmentEntity a) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", a.getId());
    m.put("objectType", a.getObjectType());
    m.put("objectId", a.getObjectId());
    m.put("fileName", a.getFileName());
    m.put("contentType", a.getContentType());
    m.put("storageUrl", a.getStorageUrl());
    m.put("sizeBytes", a.getSizeBytes());
    m.put("note", a.getNote());
    m.put("createdAt", a.getCreatedAt());
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
