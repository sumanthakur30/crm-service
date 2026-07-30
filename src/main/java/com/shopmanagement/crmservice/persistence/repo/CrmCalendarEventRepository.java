package com.shopmanagement.crmservice.persistence.repo;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.crmservice.persistence.entity.CrmCalendarEventEntity;

public interface CrmCalendarEventRepository extends JpaRepository<CrmCalendarEventEntity, Long> {

  Optional<CrmCalendarEventEntity> findByTenantIdAndIdAndDeletedAtIsNull(String tenantId, Long id);

  List<CrmCalendarEventEntity> findByTenantIdAndDeletedAtIsNullOrderByStartsAtAsc(String tenantId);

  List<CrmCalendarEventEntity> findByTenantIdAndStartsAtGreaterThanEqualAndDeletedAtIsNullOrderByStartsAtAsc(
      String tenantId, Instant from);
}
