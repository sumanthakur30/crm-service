package com.shopmanagement.crmservice.persistence.repo;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shopmanagement.crmservice.persistence.entity.CrmLeadEntity;

public interface CrmLeadRepository extends JpaRepository<CrmLeadEntity, Long> {

  Optional<CrmLeadEntity> findByTenantIdAndIdAndDeletedAtIsNull(String tenantId, Long id);

  List<CrmLeadEntity> findByTenantIdAndDeletedAtIsNull(String tenantId);

  List<CrmLeadEntity> findByTenantIdAndContactIdIsNullAndDeletedAtIsNull(
      String tenantId, Pageable pageable);

  List<CrmLeadEntity> findByTenantIdAndPhoneAndDeletedAtIsNull(String tenantId, String phone);

  List<CrmLeadEntity> findByTenantIdAndEmailAndDeletedAtIsNull(String tenantId, String email);

  List<CrmLeadEntity> findByTenantIdAndEmailIgnoreCaseAndDeletedAtIsNull(String tenantId, String email);

  List<CrmLeadEntity> findByTenantIdAndAccountIdAndDeletedAtIsNull(String tenantId, Long accountId);

  long countByTenantIdAndOwnerUserIdAndStatusAndDeletedAtIsNull(
      String tenantId, String ownerUserId, String status);

  List<CrmLeadEntity> findByTenantIdAndStatusAndUpdatedAtBeforeAndDeletedAtIsNull(
      String tenantId, String status, Instant before);

  @Query(
      """
      SELECT COALESCE(l.sourceCode, 'UNKNOWN'), COUNT(l)
      FROM CrmLeadEntity l
      WHERE l.tenantId = :tenantId AND l.deletedAt IS NULL
      GROUP BY COALESCE(l.sourceCode, 'UNKNOWN')
      ORDER BY COUNT(l) DESC
      """)
  List<Object[]> countBySource(@Param("tenantId") String tenantId);

  @Query(
      """
      SELECT COALESCE(l.utmSource, 'UNKNOWN'), COUNT(l)
      FROM CrmLeadEntity l
      WHERE l.tenantId = :tenantId AND l.deletedAt IS NULL
      GROUP BY COALESCE(l.utmSource, 'UNKNOWN')
      ORDER BY COUNT(l) DESC
      """)
  List<Object[]> countByUtmSource(@Param("tenantId") String tenantId);

  @Query(
      """
      SELECT l.campaignId, COUNT(l)
      FROM CrmLeadEntity l
      WHERE l.tenantId = :tenantId AND l.deletedAt IS NULL AND l.campaignId IS NOT NULL
      GROUP BY l.campaignId
      ORDER BY COUNT(l) DESC
      """)
  List<Object[]> countByCampaign(@Param("tenantId") String tenantId);

  @Query(
      """
      SELECT l.stageId, COUNT(l)
      FROM CrmLeadEntity l
      WHERE l.tenantId = :tenantId AND l.deletedAt IS NULL
      GROUP BY l.stageId
      """)
  List<Object[]> countByStage(@Param("tenantId") String tenantId);
  @Query(
      """
      SELECT l FROM CrmLeadEntity l
      WHERE l.tenantId = :tenantId AND l.deletedAt IS NULL
        AND (:status IS NULL OR l.status = :status)
        AND (:stageId IS NULL OR l.stageId = :stageId)
        AND (:pipelineId IS NULL OR l.pipelineId = :pipelineId)
        AND (:ownerUserId IS NULL OR l.ownerUserId = :ownerUserId)
        AND (:sourceCode IS NULL OR :sourceCode = '' OR l.sourceCode = :sourceCode)
        AND (
          :unassignedOnly IS NULL OR :unassignedOnly = '' OR :unassignedOnly = 'false'
          OR l.ownerUserId IS NULL OR l.ownerUserId = ''
        )
        AND (
          :followUp IS NULL OR :followUp = ''
          OR (
            :followUp = 'NONE'
            AND function('jsonb_extract_path_text', l.attributes, 'profile', 'nextFollowUpDate') IS NULL
          )
          OR (
            :followUp = 'DUE_TODAY'
            AND function('jsonb_extract_path_text', l.attributes, 'profile', 'nextFollowUpDate') = :today
          )
          OR (
            :followUp = 'OVERDUE'
            AND function('jsonb_extract_path_text', l.attributes, 'profile', 'nextFollowUpDate') IS NOT NULL
            AND function('jsonb_extract_path_text', l.attributes, 'profile', 'nextFollowUpDate') < :today
          )
        )
        AND (
          :course IS NULL OR :course = ''
          OR LOWER(COALESCE(function('jsonb_extract_path_text', l.attributes, 'course'), '')) = LOWER(:course)
          OR EXISTS (
            SELECT v.id FROM CrmFieldValueEntity v, CrmFieldDefEntity d
            WHERE v.fieldDefId = d.id
              AND v.tenantId = l.tenantId
              AND v.objectType = 'LEAD'
              AND v.objectId = l.id
              AND v.deletedAt IS NULL
              AND d.deletedAt IS NULL
              AND d.code = 'course'
              AND LOWER(COALESCE(v.valueText, '')) = LOWER(:course)
          )
        )
        AND (
          :scopeMode IS NULL OR :scopeMode = 'ORG'
          OR (
            :scopeMode = 'OWN' AND l.ownerUserId = :scopeUserId
          )
          OR (
            :scopeMode = 'TEAM' AND (
              l.ownerUserId = :scopeUserId
              OR (l.teamId IS NOT NULL AND l.teamId IN :scopeTeamIds)
            )
          )
        )
        AND (
          :q IS NULL OR :q = '' OR
          LOWER(l.title) LIKE LOWER(CONCAT('%', :q, '%')) OR
          LOWER(COALESCE(l.displayName, '')) LIKE LOWER(CONCAT('%', :q, '%')) OR
          LOWER(COALESCE(l.companyName, '')) LIKE LOWER(CONCAT('%', :q, '%')) OR
          LOWER(COALESCE(l.email, '')) LIKE LOWER(CONCAT('%', :q, '%')) OR
          LOWER(COALESCE(l.phone, '')) LIKE LOWER(CONCAT('%', :q, '%'))
        )
      """)
  Page<CrmLeadEntity> search(
      @Param("tenantId") String tenantId,
      @Param("status") String status,
      @Param("stageId") Long stageId,
      @Param("pipelineId") Long pipelineId,
      @Param("ownerUserId") String ownerUserId,
      @Param("sourceCode") String sourceCode,
      @Param("unassignedOnly") String unassignedOnly,
      @Param("followUp") String followUp,
      @Param("today") String today,
      @Param("course") String course,
      @Param("q") String q,
      @Param("scopeMode") String scopeMode,
      @Param("scopeUserId") String scopeUserId,
      @Param("scopeTeamIds") List<String> scopeTeamIds,
      Pageable pageable);

  List<CrmLeadEntity>
      findByTenantIdAndStatusAndScoreGreaterThanEqualAndDeletedAtIsNullOrderByScoreDesc(
          String tenantId, String status, int minScore, Pageable pageable);
}
