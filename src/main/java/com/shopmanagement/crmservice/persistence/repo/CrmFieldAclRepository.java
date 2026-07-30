package com.shopmanagement.crmservice.persistence.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.crmservice.persistence.entity.CrmFieldAclEntity;

public interface CrmFieldAclRepository extends JpaRepository<CrmFieldAclEntity, Long> {

  Optional<CrmFieldAclEntity> findByTenantIdAndIdAndDeletedAtIsNull(String tenantId, Long id);

  List<CrmFieldAclEntity> findByTenantIdAndRoleCodeAndDeletedAtIsNull(String tenantId, String roleCode);

  List<CrmFieldAclEntity> findByTenantIdAndDeletedAtIsNull(String tenantId);

  Optional<CrmFieldAclEntity> findByTenantIdAndRoleCodeAndObjectTypeAndFieldNameAndDeletedAtIsNull(
      String tenantId, String roleCode, String objectType, String fieldName);
}
