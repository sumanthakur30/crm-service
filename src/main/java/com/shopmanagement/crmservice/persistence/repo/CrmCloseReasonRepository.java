package com.shopmanagement.crmservice.persistence.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.crmservice.persistence.entity.CrmCloseReasonEntity;

public interface CrmCloseReasonRepository extends JpaRepository<CrmCloseReasonEntity, Long> {

  List<CrmCloseReasonEntity> findByTenantIdAndDeletedAtIsNullAndActiveTrueOrderBySortOrderAscNameAsc(
      String tenantId);

  Optional<CrmCloseReasonEntity> findByTenantIdAndCodeAndDeletedAtIsNull(String tenantId, String code);

  long countByTenantIdAndDeletedAtIsNull(String tenantId);
}
