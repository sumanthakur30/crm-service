package com.shopmanagement.crmservice.persistence.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shopmanagement.crmservice.persistence.entity.CrmRrCursorEntity;

import jakarta.persistence.LockModeType;

public interface CrmRrCursorRepository extends JpaRepository<CrmRrCursorEntity, CrmRrCursorEntity.Pk> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT c FROM CrmRrCursorEntity c WHERE c.tenantId = :tenantId AND c.teamId = :teamId")
  Optional<CrmRrCursorEntity> findForUpdate(
      @Param("tenantId") String tenantId, @Param("teamId") String teamId);
}
