package com.shopmanagement.crmservice.persistence.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.crmservice.persistence.entity.CrmSequenceStepEntity;

public interface CrmSequenceStepRepository extends JpaRepository<CrmSequenceStepEntity, Long> {

  List<CrmSequenceStepEntity> findByTenantIdAndSequenceIdAndDeletedAtIsNullOrderBySortOrderAsc(
      String tenantId, Long sequenceId);
}
