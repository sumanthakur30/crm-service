package com.shopmanagement.crmservice.persistence.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.crmservice.persistence.entity.CrmTenantEnterpriseEntity;

public interface CrmTenantEnterpriseRepository extends JpaRepository<CrmTenantEnterpriseEntity, String> {}
