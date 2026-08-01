package com.shopmanagement.crmservice.persistence.repo;



import java.util.List;



import org.springframework.data.jpa.repository.JpaRepository;



import com.shopmanagement.crmservice.persistence.entity.CrmCsatResponseEntity;



public interface CrmCsatResponseRepository extends JpaRepository<CrmCsatResponseEntity, Long> {



  List<CrmCsatResponseEntity> findByTenantIdAndCaseIdOrderByCreatedAtDesc(String tenantId, Long caseId);

}


