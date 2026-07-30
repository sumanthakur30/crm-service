package com.shopmanagement.crmservice.persistence.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.crmservice.persistence.entity.CrmAiInsightEntity;

public interface CrmAiInsightRepository extends JpaRepository<CrmAiInsightEntity, Long> {

  List<CrmAiInsightEntity> findByTenantIdAndRelatedTypeAndRelatedIdOrderByCreatedAtDesc(
      String tenantId, String relatedType, Long relatedId);

  List<CrmAiInsightEntity> findByTenantIdAndInsightTypeOrderByCreatedAtDesc(
      String tenantId, String insightType);
}
