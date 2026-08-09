package com.shopmanagement.crmservice.persistence.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.crmservice.persistence.entity.CrmQualificationSchemaEntity;

public interface CrmQualificationSchemaRepository
    extends JpaRepository<CrmQualificationSchemaEntity, Long> {

  List<CrmQualificationSchemaEntity> findByTenantIdAndDeletedAtIsNullOrderByCodeAsc(String tenantId);

  Optional<CrmQualificationSchemaEntity> findByTenantIdAndIdAndDeletedAtIsNull(
      String tenantId, Long id);

  Optional<CrmQualificationSchemaEntity> findByTenantIdAndCodeAndDeletedAtIsNull(
      String tenantId, String code);
}
