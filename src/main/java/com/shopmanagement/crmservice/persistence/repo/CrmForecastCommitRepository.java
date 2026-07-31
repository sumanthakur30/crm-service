package com.shopmanagement.crmservice.persistence.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.crmservice.persistence.entity.CrmForecastCommitEntity;

public interface CrmForecastCommitRepository extends JpaRepository<CrmForecastCommitEntity, Long> {

  List<CrmForecastCommitEntity> findByTenantIdAndPeriodYmOrderByUpdatedAtDesc(
      String tenantId, String periodYm);

  Optional<CrmForecastCommitEntity> findByTenantIdAndUserIdAndPeriodYm(
      String tenantId, String userId, String periodYm);
}
