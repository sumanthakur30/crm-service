package com.shopmanagement.crmservice.persistence.repo;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.crmservice.persistence.entity.CrmJobEntity;

public interface CrmJobRepository extends JpaRepository<CrmJobEntity, Long> {

  List<CrmJobEntity> findByStatusAndRunAtLessThanEqual(String status, Instant runAt, Pageable pageable);
}
