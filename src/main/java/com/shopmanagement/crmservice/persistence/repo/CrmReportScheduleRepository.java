package com.shopmanagement.crmservice.persistence.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.crmservice.persistence.entity.CrmReportScheduleEntity;

public interface CrmReportScheduleRepository extends JpaRepository<CrmReportScheduleEntity, Long> {

  Optional<CrmReportScheduleEntity> findByTenantIdAndIdAndDeletedAtIsNull(String tenantId, Long id);

  Optional<CrmReportScheduleEntity> findByTenantIdAndCodeAndDeletedAtIsNull(String tenantId, String code);

  List<CrmReportScheduleEntity> findByTenantIdAndDeletedAtIsNullOrderByNameAsc(String tenantId);

  List<CrmReportScheduleEntity> findByTenantIdAndActiveTrueAndDeletedAtIsNull(String tenantId);
}
