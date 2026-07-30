package com.shopmanagement.crmservice.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.shopmanagement.crmservice.persistence.entity.CrmLeadEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmLeadRepository;
import com.shopmanagement.crmservice.support.TenantIds;

@Service
public class LeadMergeService {

  private final CrmLeadRepository leadRepository;
  private final TimelineService timelineService;

  public LeadMergeService(CrmLeadRepository leadRepository, TimelineService timelineService) {
    this.leadRepository = leadRepository;
    this.timelineService = timelineService;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> findDuplicates(Long leadId) {
    String tenantId = TenantIds.require();
    CrmLeadEntity lead = requireLead(tenantId, leadId);
    Set<Long> seen = new LinkedHashSet<>();
    List<Map<String, Object>> out = new ArrayList<>();

    if (lead.getPhone() != null && !lead.getPhone().isBlank()) {
      for (CrmLeadEntity other :
          leadRepository.findByTenantIdAndPhoneAndDeletedAtIsNull(tenantId, lead.getPhone())) {
        if (isDuplicateCandidate(lead, other) && seen.add(other.getId())) {
          out.add(toSummary(other, "PHONE"));
        }
      }
    }
    if (lead.getEmail() != null && !lead.getEmail().isBlank()) {
      for (CrmLeadEntity other :
          leadRepository.findByTenantIdAndEmailAndDeletedAtIsNull(tenantId, lead.getEmail())) {
        if (isDuplicateCandidate(lead, other) && seen.add(other.getId())) {
          out.add(toSummary(other, "EMAIL"));
        }
      }
    }
    return out;
  }

  @Transactional
  public Map<String, Object> merge(Long survivorId, Long duplicateId) {
    String tenantId = TenantIds.require();
    if (survivorId.equals(duplicateId)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot merge a lead into itself");
    }
    CrmLeadEntity survivor = requireLead(tenantId, survivorId);
    CrmLeadEntity duplicate = requireLead(tenantId, duplicateId);
    if ("DUPLICATE".equalsIgnoreCase(duplicate.getStatus())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate lead already merged");
    }

    copyIfBlank(survivor, duplicate);
    mergeAttributes(survivor, duplicate);

    duplicate.setStatus("DUPLICATE");
    duplicate.setMergedIntoLeadId(survivor.getId());
    duplicate.softDelete();
    survivor.touch();

    leadRepository.save(survivor);
    leadRepository.save(duplicate);

    timelineService.recordEvent(
        "LEAD",
        survivorId,
        "LEAD_MERGED",
        "Merged duplicate lead " + duplicateId,
        Map.of("duplicateId", duplicateId, "survivorId", survivorId));
    timelineService.recordEvent(
        "LEAD",
        duplicateId,
        "LEAD_MERGED_INTO",
        "Merged into lead " + survivorId,
        Map.of("duplicateId", duplicateId, "survivorId", survivorId));

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("survivorId", survivorId);
    out.put("duplicateId", duplicateId);
    out.put("status", "MERGED");
    return out;
  }

  private static boolean isDuplicateCandidate(CrmLeadEntity self, CrmLeadEntity other) {
    if (other.getId().equals(self.getId())) {
      return false;
    }
    return !"DUPLICATE".equalsIgnoreCase(other.getStatus());
  }

  private CrmLeadEntity requireLead(String tenantId, Long id) {
    return leadRepository
        .findByTenantIdAndIdAndDeletedAtIsNull(tenantId, id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead not found"));
  }

  private static void copyIfBlank(CrmLeadEntity survivor, CrmLeadEntity duplicate) {
    if (blank(survivor.getDisplayName())) {
      survivor.setDisplayName(duplicate.getDisplayName());
    }
    if (blank(survivor.getCompanyName())) {
      survivor.setCompanyName(duplicate.getCompanyName());
    }
    if (blank(survivor.getEmail())) {
      survivor.setEmail(duplicate.getEmail());
    }
    if (blank(survivor.getPhone())) {
      survivor.setPhone(duplicate.getPhone());
    }
    if (blank(survivor.getSourceCode())) {
      survivor.setSourceCode(duplicate.getSourceCode());
    }
    if (blank(survivor.getOwnerUserId())) {
      survivor.setOwnerUserId(duplicate.getOwnerUserId());
    }
    if (blank(survivor.getTeamId())) {
      survivor.setTeamId(duplicate.getTeamId());
    }
    if (survivor.getAmount() == null) {
      survivor.setAmount(duplicate.getAmount());
    }
    if (survivor.getAccountId() == null) {
      survivor.setAccountId(duplicate.getAccountId());
    }
    if (survivor.getContactId() == null) {
      survivor.setContactId(duplicate.getContactId());
    }
    if (blank(survivor.getStateCode())) {
      survivor.setStateCode(duplicate.getStateCode());
    }
    if (blank(survivor.getPincode())) {
      survivor.setPincode(duplicate.getPincode());
    }
    if (blank(survivor.getFormKey())) {
      survivor.setFormKey(duplicate.getFormKey());
    }
    if (survivor.getCampaignId() == null) {
      survivor.setCampaignId(duplicate.getCampaignId());
    }
    if (blank(survivor.getUtmSource())) {
      survivor.setUtmSource(duplicate.getUtmSource());
    }
    if (blank(survivor.getUtmMedium())) {
      survivor.setUtmMedium(duplicate.getUtmMedium());
    }
    if (blank(survivor.getUtmCampaign())) {
      survivor.setUtmCampaign(duplicate.getUtmCampaign());
    }
    if (blank(survivor.getUtmContent())) {
      survivor.setUtmContent(duplicate.getUtmContent());
    }
    if (blank(survivor.getUtmTerm())) {
      survivor.setUtmTerm(duplicate.getUtmTerm());
    }
  }

  private static void mergeAttributes(CrmLeadEntity survivor, CrmLeadEntity duplicate) {
    Map<String, Object> merged =
        survivor.getAttributes() == null
            ? new LinkedHashMap<>()
            : new LinkedHashMap<>(survivor.getAttributes());
    if (duplicate.getAttributes() != null) {
      for (Map.Entry<String, Object> e : duplicate.getAttributes().entrySet()) {
        merged.putIfAbsent(e.getKey(), e.getValue());
      }
    }
    survivor.setAttributes(merged);

    Map<String, Object> refs =
        survivor.getExternalRefs() == null
            ? new LinkedHashMap<>()
            : new LinkedHashMap<>(survivor.getExternalRefs());
    if (duplicate.getExternalRefs() != null) {
      for (Map.Entry<String, Object> e : duplicate.getExternalRefs().entrySet()) {
        refs.putIfAbsent(e.getKey(), e.getValue());
      }
    }
    survivor.setExternalRefs(refs);
  }

  private static Map<String, Object> toSummary(CrmLeadEntity lead, String matchedOn) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", lead.getId());
    m.put("title", lead.getTitle());
    m.put("displayName", lead.getDisplayName());
    m.put("email", lead.getEmail());
    m.put("phone", lead.getPhone());
    m.put("status", lead.getStatus());
    m.put("matchedOn", matchedOn);
    return m;
  }

  private static boolean blank(String v) {
    return v == null || v.isBlank();
  }
}
