package com.shopmanagement.crmservice.persistence.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.crmservice.persistence.entity.CrmTimelineEventEntity;

public interface CrmTimelineEventRepository extends JpaRepository<CrmTimelineEventEntity, Long> {

  List<CrmTimelineEventEntity>
      findByTenantIdAndRelatedTypeAndRelatedIdOrderByOccurredAtDesc(
          String tenantId, String relatedType, Long relatedId);
}
