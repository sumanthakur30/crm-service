package com.shopmanagement.crmservice.persistence.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.crmservice.persistence.entity.CrmObjectTagEntity;

public interface CrmObjectTagRepository extends JpaRepository<CrmObjectTagEntity, Long> {

  List<CrmObjectTagEntity> findByTenantIdAndObjectTypeAndObjectId(String tenantId, String objectType, Long objectId);

  Optional<CrmObjectTagEntity> findByTenantIdAndObjectTypeAndObjectIdAndTagId(
      String tenantId, String objectType, Long objectId, Long tagId);

  void deleteByTenantIdAndObjectTypeAndObjectIdAndTagId(
      String tenantId, String objectType, Long objectId, Long tagId);
}
