package com.shopmanagement.crmservice.persistence.repo;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shopmanagement.crmservice.persistence.entity.CrmOpportunityEntity;

public interface CrmOpportunityRepository extends JpaRepository<CrmOpportunityEntity, Long> {

  Optional<CrmOpportunityEntity> findByTenantIdAndIdAndDeletedAtIsNull(String tenantId, Long id);

  List<CrmOpportunityEntity> findByTenantIdAndAccountIdAndDeletedAtIsNull(String tenantId, Long accountId);

  List<CrmOpportunityEntity> findByTenantIdAndStatusAndUpdatedAtBeforeAndDeletedAtIsNull(
      String tenantId, String status, Instant before);

  @Query(
      """
      SELECT o.stageId, COUNT(o), COALESCE(SUM(o.amount), 0)
      FROM CrmOpportunityEntity o
      WHERE o.tenantId = :tenantId AND o.deletedAt IS NULL AND o.status = 'OPEN'
      GROUP BY o.stageId
      """)
  List<Object[]> openFunnelByStage(@Param("tenantId") String tenantId);

  long countByTenantIdAndStatusAndDeletedAtIsNull(String tenantId, String status);
  @Query(
      """
      SELECT o FROM CrmOpportunityEntity o
      WHERE o.tenantId = :tenantId AND o.deletedAt IS NULL
        AND (:status IS NULL OR o.status = :status)
        AND (:stageId IS NULL OR o.stageId = :stageId)
        AND (
          :scopeMode IS NULL OR :scopeMode = 'ORG'
          OR (
            :scopeMode = 'OWN' AND o.ownerUserId = :scopeUserId
          )
          OR (
            :scopeMode = 'TEAM' AND (
              o.ownerUserId = :scopeUserId
              OR (o.teamId IS NOT NULL AND o.teamId IN :scopeTeamIds)
            )
          )
        )
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
      @Param("scopeMode") String scopeMode,
      @Param("scopeUserId") String scopeUserId,
      @Param("scopeTeamIds") List<String> scopeTeamIds,
      Pageable pageable);
}
