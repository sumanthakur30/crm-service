package com.shopmanagement.crmservice.persistence.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.crmservice.persistence.entity.CrmJourneyEntity;

public interface CrmJourneyRepository extends JpaRepository<CrmJourneyEntity, Long> {
  List<CrmJourneyEntity> findByTenantIdAndDeletedAtIsNullOrderByIdDesc(String tenantId);

  Optional<CrmJourneyEntity> findByTenantIdAndIdAndDeletedAtIsNull(String tenantId, Long id);
}
