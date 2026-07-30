package com.shopmanagement.crmservice.persistence.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shopmanagement.crmservice.persistence.entity.CrmScoreEventEntity;

public interface CrmScoreEventRepository extends JpaRepository<CrmScoreEventEntity, Long> {

  List<CrmScoreEventEntity> findByTenantIdAndLeadIdOrderByCreatedAtDesc(String tenantId, Long leadId);

  @Query(
      """
      SELECT COALESCE(SUM(e.points), 0)
      FROM CrmScoreEventEntity e
      WHERE e.tenantId = :tenantId AND e.leadId = :leadId
      """)
  int sumPointsByTenantIdAndLeadId(@Param("tenantId") String tenantId, @Param("leadId") Long leadId);
}
