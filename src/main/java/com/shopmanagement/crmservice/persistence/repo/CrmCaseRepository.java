package com.shopmanagement.crmservice.persistence.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.crmservice.persistence.entity.CrmCaseEntity;

public interface CrmCaseRepository extends JpaRepository<CrmCaseEntity, Long> {

  List<CrmCaseEntity> findByTenantIdAndDeletedAtIsNullOrderByUpdatedAtDesc(String tenantId);

  List<CrmCaseEntity> findByTenantIdAndStatusAndDeletedAtIsNullOrderByUpdatedAtDesc(
      String tenantId, String status);

  Optional<CrmCaseEntity> findByTenantIdAndIdAndDeletedAtIsNull(String tenantId, Long id);

  Optional<CrmCaseEntity> findByCsatPublicTokenAndDeletedAtIsNull(String csatPublicToken);
}
