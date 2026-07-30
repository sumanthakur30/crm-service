package com.shopmanagement.crmservice.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "crm_attachment")
@Getter
@Setter
public class CrmAttachmentEntity extends TenantAuditableEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "object_type", nullable = false, length = 32)
  private String objectType;

  @Column(name = "object_id", nullable = false)
  private Long objectId;

  @Column(name = "file_name", nullable = false, length = 256)
  private String fileName;

  @Column(name = "content_type", length = 128)
  private String contentType;

  @Column(name = "storage_url", length = 1024)
  private String storageUrl;

  @Column(name = "size_bytes")
  private Long sizeBytes;

  @Column(length = 512)
  private String note;
}
