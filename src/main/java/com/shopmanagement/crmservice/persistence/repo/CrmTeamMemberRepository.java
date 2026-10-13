package com.shopmanagement.crmservice.persistence.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shopmanagement.crmservice.persistence.entity.CrmTeamMemberEntity;

import jakarta.persistence.LockModeType;

public interface CrmTeamMemberRepository extends JpaRepository<CrmTeamMemberEntity, Long> {

  List<CrmTeamMemberEntity> findByTenantIdAndTeamIdAndActiveTrueAndDeletedAtIsNullOrderBySortOrderAscIdAsc(
      String tenantId, String teamId);

  Optional<CrmTeamMemberEntity> findByTenantIdAndIdAndDeletedAtIsNull(String tenantId, Long id);

  Optional<CrmTeamMemberEntity> findByTenantIdAndTeamIdAndUserIdAndDeletedAtIsNull(
      String tenantId, String teamId, String userId);

  List<CrmTeamMemberEntity> findByTenantIdAndUserIdAndActiveTrueAndDeletedAtIsNull(
      String tenantId, String userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "SELECT m FROM CrmTeamMemberEntity m WHERE m.tenantId = :tenantId AND m.teamId = :teamId "
          + "AND m.active = true AND m.deletedAt IS NULL ORDER BY m.sortOrder ASC, m.id ASC")
  List<CrmTeamMemberEntity> findActiveForUpdate(
      @Param("tenantId") String tenantId, @Param("teamId") String teamId);

  long countByTenantIdAndActiveTrueAndDeletedAtIsNull(String tenantId);
}
