package com.shopmanagement.crmservice.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.shopmanagement.crmservice.persistence.entity.CrmAccountEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmDuplicateRuleEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmLeadEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmAccountRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmLeadRepository;
import com.shopmanagement.crmservice.support.TenantIds;

@Service
public class LeadMergeService {

  private final CrmLeadRepository leadRepository;
  private final CrmAccountRepository accountRepository;
  private final TimelineService timelineService;
  private final DuplicateRuleService duplicateRuleService;

  public LeadMergeService(
      CrmLeadRepository leadRepository,
      CrmAccountRepository accountRepository,
      TimelineService timelineService,
      DuplicateRuleService duplicateRuleService) {
    this.leadRepository = leadRepository;
    this.accountRepository = accountRepository;
    this.timelineService = timelineService;
    this.duplicateRuleService = duplicateRuleService;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> findDuplicates(Long leadId) {
    String tenantId = TenantIds.require();
    CrmLeadEntity lead = requireLead(tenantId, leadId);
    List<CrmDuplicateRuleEntity> rules = duplicateRuleService.enabledRules("LEAD");
    if (rules.isEmpty()) {
      return List.of();
    }

    Map<Long, Map<String, Object>> byId = new LinkedHashMap<>();
    for (CrmDuplicateRuleEntity rule : rules) {
      String field = rule.getMatchField() == null ? "" : rule.getMatchField().toUpperCase(Locale.ROOT);
      String needle = extractLeadValue(lead, field);
      String normalized = DuplicateRuleService.normalize(needle, rule.getNormalizeMode());
      if (normalized == null) {
        continue;
      }
      for (CrmLeadEntity other : leadRepository.findByTenantIdAndDeletedAtIsNull(tenantId)) {
        if (!isDuplicateCandidate(lead, other)) {
          continue;
        }
        String otherRaw = extractLeadValue(other, field);
        String otherNorm = DuplicateRuleService.normalize(otherRaw, rule.getNormalizeMode());
        if (otherNorm != null && Objects.equals(normalized, otherNorm)) {
          mergeHit(byId, other, field);
        }
      }
      if ("GSTIN".equals(field)) {
        for (CrmAccountEntity account :
            accountRepository.findByTenantIdAndDeletedAtIsNullOrderByNameAsc(tenantId)) {
          String acctNorm = DuplicateRuleService.normalize(account.getGstin(), rule.getNormalizeMode());
          if (acctNorm == null || !Objects.equals(normalized, acctNorm)) {
            continue;
          }
          for (CrmLeadEntity other :
              leadRepository.findByTenantIdAndAccountIdAndDeletedAtIsNull(tenantId, account.getId())) {
            if (isDuplicateCandidate(lead, other)) {
              mergeHit(byId, other, "GSTIN_ACCOUNT");
            }
          }
        }
      }
    }
    return new ArrayList<>(byId.values());
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

  private static void mergeHit(Map<Long, Map<String, Object>> byId, CrmLeadEntity other, String matchedOn) {
    Map<String, Object> existing = byId.get(other.getId());
    if (existing == null) {
      byId.put(other.getId(), toSummary(other, matchedOn));
      return;
    }
    Object prev = existing.get("matchedOn");
    Set<String> fields = new LinkedHashSet<>();
    if (prev != null) {
      for (String p : String.valueOf(prev).split(",")) {
        if (!p.isBlank()) {
          fields.add(p.trim());
        }
      }
    }
    fields.add(matchedOn);
    existing.put("matchedOn", String.join(",", fields));
  }

  private static String extractLeadValue(CrmLeadEntity lead, String field) {
    return switch (field) {
      case "PHONE" -> lead.getPhone();
      case "EMAIL" -> lead.getEmail();
      case "GSTIN" -> {
        if (lead.getAttributes() != null && lead.getAttributes().get("gstin") != null) {
          yield String.valueOf(lead.getAttributes().get("gstin"));
        }
        yield null;
      }
      default -> null;
    };
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
