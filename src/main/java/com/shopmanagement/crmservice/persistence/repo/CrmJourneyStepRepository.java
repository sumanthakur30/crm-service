package com.shopmanagement.crmservice.persistence.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.crmservice.persistence.entity.CrmJourneyStepEntity;

public interface CrmJourneyStepRepository extends JpaRepository<CrmJourneyStepEntity, Long> {
  List<CrmJourneyStepEntity> findByTenantIdAndJourneyIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(
      String tenantId, Long journeyId);
}
