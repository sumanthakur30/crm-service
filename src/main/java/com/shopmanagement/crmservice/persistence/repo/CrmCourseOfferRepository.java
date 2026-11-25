package com.shopmanagement.crmservice.persistence.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.crmservice.persistence.entity.CrmCourseOfferEntity;

public interface CrmCourseOfferRepository extends JpaRepository<CrmCourseOfferEntity, Long> {

  List<CrmCourseOfferEntity> findByTenantIdAndDeletedAtIsNullOrderByIdAsc(String tenantId);
}
