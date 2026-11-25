package com.shopmanagement.crmservice.persistence.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.crmservice.persistence.entity.CrmCourseEntity;

public interface CrmCourseRepository extends JpaRepository<CrmCourseEntity, Long> {

  List<CrmCourseEntity> findByTenantIdAndDeletedAtIsNullOrderByNameAsc(String tenantId);

  Optional<CrmCourseEntity> findByTenantIdAndCodeAndDeletedAtIsNull(String tenantId, String code);
}
