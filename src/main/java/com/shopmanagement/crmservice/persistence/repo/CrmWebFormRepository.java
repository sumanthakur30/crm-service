package com.shopmanagement.crmservice.persistence.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.crmservice.persistence.entity.CrmWebFormEntity;

public interface CrmWebFormRepository extends JpaRepository<CrmWebFormEntity, Long> {

  Optional<CrmWebFormEntity> findByPublicKeyAndDeletedAtIsNull(String publicKey);

  Optional<CrmWebFormEntity> findByTenantIdAndIdAndDeletedAtIsNull(String tenantId, Long id);

  List<CrmWebFormEntity> findByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc(String tenantId);

  long countByTenantIdAndDeletedAtIsNull(String tenantId);
}
