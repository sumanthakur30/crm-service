package com.shopmanagement.crmservice.persistence.repo;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shopmanagement.crmservice.persistence.entity.CrmOpportunityEntity;

public interface CrmOpportunityRepository extends JpaRepository<CrmOpportunityEntity, Long> {

  Optional<CrmOpportunityEntity> findByTenantIdAndIdAndDeletedAtIsNull(String tenantId, Long id);

  @Query(
      """
      SELECT o FROM CrmOpportunityEntity o
      WHERE o.tenantId = :tenantId AND o.deletedAt IS NULL
        AND (:status IS NULL OR o.status = :status)
        AND (:stageId IS NULL OR o.stageId = :stageId)
        AND (
          :q IS NULL OR :q = '' OR
          LOWER(o.name) LIKE LOWER(CONCAT('%', :q, '%'))
        )
      """)
  Page<CrmOpportunityEntity> search(
      @Param("tenantId") String tenantId,
      @Param("status") String status,
      @Param("stageId") Long stageId,
      @Param("q") String q,
      Pageable pageable);
}
