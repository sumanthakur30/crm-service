package com.shopmanagement.crmservice.persistence.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.crmservice.persistence.entity.CrmSlaPolicyEntity;

public interface CrmSlaPolicyRepository extends JpaRepository<CrmSlaPolicyEntity, Long> {

  Optional<CrmSlaPolicyEntity> findByTenantIdAndCodeAndDeletedAtIsNull(String tenantId, String code);

  List<CrmSlaPolicyEntity> findByTenantIdAndActiveTrueAndDeletedAtIsNull(String tenantId);
}
