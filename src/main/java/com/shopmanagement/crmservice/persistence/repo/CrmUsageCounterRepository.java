package com.shopmanagement.crmservice.persistence.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.crmservice.persistence.entity.CrmUsageCounterEntity;

public interface CrmUsageCounterRepository
    extends JpaRepository<CrmUsageCounterEntity, CrmUsageCounterEntity.Pk> {}
