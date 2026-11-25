package com.shopmanagement.crmservice.persistence.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.crmservice.persistence.entity.CrmAdmissionSessionEntity;

public interface CrmAdmissionSessionRepository extends JpaRepository<CrmAdmissionSessionEntity, Long> {

  List<CrmAdmissionSessionEntity> findByTenantIdAndDeletedAtIsNullOrderByNameAsc(String tenantId);

  Optional<CrmAdmissionSessionEntity> findByTenantIdAndCodeAndDeletedAtIsNull(String tenantId, String code);
}
