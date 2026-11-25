package com.shopmanagement.crmservice.persistence.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.crmservice.persistence.entity.CrmFieldMapEntity;

public interface CrmFieldMapRepository extends JpaRepository<CrmFieldMapEntity, Long> {

  List<CrmFieldMapEntity> findByFormIdOrderByIdAsc(Long formId);

  List<CrmFieldMapEntity> findByAssetIdOrderByIdAsc(Long assetId);

  void deleteByFormId(Long formId);

  void deleteByAssetId(Long assetId);
}
