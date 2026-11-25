package com.shopmanagement.crmservice.service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.shopmanagement.crmservice.persistence.entity.CrmLeadEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmLeadRepository;
import com.shopmanagement.crmservice.support.PhoneCandidates;

/** Exact phone or email match against open leads. Does not scan the tenant. */
@Service
public class LeadIdentityService {

  private final CrmLeadRepository leadRepository;

  public LeadIdentityService(CrmLeadRepository leadRepository) {
    this.leadRepository = leadRepository;
  }

  public Optional<CrmLeadEntity> findOpenMatch(String tenantId, String phone, String email) {
    for (String candidate : PhoneCandidates.of(phone)) {
      Optional<CrmLeadEntity> hit = firstOpen(leadRepository.findByTenantIdAndPhoneAndDeletedAtIsNull(tenantId, candidate));
      if (hit.isPresent()) {
        return hit;
      }
    }
    if (email != null && !email.isBlank()) {
      return firstOpen(
          leadRepository.findByTenantIdAndEmailIgnoreCaseAndDeletedAtIsNull(tenantId, email.trim()));
    }
    return Optional.empty();
  }

  private static Optional<CrmLeadEntity> firstOpen(List<CrmLeadEntity> leads) {
    return leads.stream()
        .filter(lead -> lead.getStatus() == null || !"DUPLICATE".equalsIgnoreCase(lead.getStatus()))
        .findFirst();
  }

  public static String sourceLabel(String sourceCode) {
    if (sourceCode == null || sourceCode.isBlank()) {
      return "unknown";
    }
    return sourceCode.trim().toLowerCase(Locale.ROOT);
  }
}
