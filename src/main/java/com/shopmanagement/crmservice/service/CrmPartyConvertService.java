package com.shopmanagement.crmservice.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.shopmanagement.crmservice.api.CrmDealApi.OpportunityUpsert;
import com.shopmanagement.crmservice.api.CrmPartyConvertApi.AccountInput;
import com.shopmanagement.crmservice.api.CrmPartyConvertApi.ContactInput;
import com.shopmanagement.crmservice.api.CrmPartyConvertApi.ConvertToCrmRequest;
import com.shopmanagement.crmservice.api.CrmPartyConvertApi.ConvertToCrmResponse;
import com.shopmanagement.crmservice.api.CrmPartyConvertApi.OpportunityInput;
import com.shopmanagement.crmservice.persistence.entity.CrmLeadEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmLeadRepository;
import com.shopmanagement.crmservice.support.TenantIds;

/**
 * CRM-internal convert: Lead → Account / Contact / optional Opportunity.
 * Does not replace ERP convert ({@link LeadConvertService}).
 */
@Service
public class CrmPartyConvertService {

  private final CrmLeadRepository leadRepository;
  private final AccountContactService accountContactService;
  private final OpportunityService opportunityService;
  private final TimelineService timelineService;

  public CrmPartyConvertService(
      CrmLeadRepository leadRepository,
      AccountContactService accountContactService,
      OpportunityService opportunityService,
      TimelineService timelineService) {
    this.leadRepository = leadRepository;
    this.accountContactService = accountContactService;
    this.opportunityService = opportunityService;
    this.timelineService = timelineService;
  }

  @Transactional
  public ConvertToCrmResponse convert(Long leadId, ConvertToCrmRequest body) {
    if (body == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Body is required");
    }
    String tenantId = TenantIds.require();
    CrmLeadEntity lead =
        leadRepository
            .findByTenantIdAndIdAndDeletedAtIsNull(tenantId, leadId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead not found"));

    Long accountId = resolveAccount(lead, body);
    Long contactId = resolveContact(lead, accountId, body);
    Long opportunityId = null;
    if (Boolean.TRUE.equals(body.createOpportunity())) {
      opportunityId = createOpportunity(lead, accountId, body.opportunity());
    }

    lead.setAccountId(accountId);
    if (contactId != null) {
      lead.setContactId(contactId);
    }
    boolean markConverted = body.markLeadConverted() == null || body.markLeadConverted();
    if (markConverted) {
      lead.setStatus("CONVERTED");
    }
    lead.touch();
    leadRepository.save(lead);

    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("accountMode", body.accountMode());
    detail.put("contactMode", body.contactMode());
    detail.put("createOpportunity", body.createOpportunity());
    detail.put("markLeadConverted", markConverted);

    timelineService.recordEvent(
        "LEAD",
        leadId,
        "LEAD_CONVERTED_CRM",
        "Converted to CRM party"
            + (opportunityId != null ? (" · opportunity #" + opportunityId) : ""),
        Map.of(
            "accountId", accountId,
            "contactId", contactId == null ? "" : contactId,
            "opportunityId", opportunityId == null ? "" : opportunityId));
    if (accountId != null) {
      timelineService.recordEvent(
          "ACCOUNT",
          accountId,
          "LEAD_LINKED",
          "Lead #" + leadId + " linked",
          Map.of("leadId", leadId, "contactId", contactId == null ? "" : contactId));
    }

    return new ConvertToCrmResponse(
        leadId, accountId, contactId, opportunityId, markConverted ? "CONVERTED" : "LINKED", detail);
  }

  private Long resolveAccount(CrmLeadEntity lead, ConvertToCrmRequest body) {
    String mode = body.accountMode() == null ? "" : body.accountMode().trim().toUpperCase();
    if ("EXISTING".equals(mode)) {
      if (body.accountId() == null) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "accountId required for EXISTING");
      }
      accountContactService.getAccount(body.accountId());
      return body.accountId();
    }
    if (!"CREATE".equals(mode)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "accountMode must be CREATE or EXISTING");
    }
    AccountInput a = body.account();
    Map<String, Object> payload = new LinkedHashMap<>();
    String name =
        a != null && a.name() != null && !a.name().isBlank()
            ? a.name().trim()
            : (lead.getCompanyName() != null && !lead.getCompanyName().isBlank()
                ? lead.getCompanyName().trim()
                : lead.getTitle());
    payload.put("name", name);
    if (a != null) {
      payload.put("gstin", a.gstin());
      payload.put("phone", blankTo(a.phone(), lead.getPhone()));
      payload.put("email", blankTo(a.email(), lead.getEmail()));
      payload.put("stateCode", a.stateCode());
      payload.put("pincode", a.pincode());
    } else {
      payload.put("phone", lead.getPhone());
      payload.put("email", lead.getEmail());
    }
    Map<String, Object> created = accountContactService.upsertAccount(payload);
    return ((Number) created.get("id")).longValue();
  }

  private Long resolveContact(CrmLeadEntity lead, Long accountId, ConvertToCrmRequest body) {
    String mode = body.contactMode() == null ? "NONE" : body.contactMode().trim().toUpperCase();
    if ("NONE".equals(mode)) {
      return null;
    }
    if ("EXISTING".equals(mode)) {
      if (body.contactId() == null) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "contactId required for EXISTING");
      }
      accountContactService.getContact(body.contactId());
      return body.contactId();
    }
    if (!"CREATE".equals(mode)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "contactMode must be CREATE, EXISTING, or NONE");
    }
    ContactInput c = body.contact();
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("accountId", accountId);
    String display =
        c != null && c.displayName() != null && !c.displayName().isBlank()
            ? c.displayName().trim()
            : (lead.getDisplayName() != null && !lead.getDisplayName().isBlank()
                ? lead.getDisplayName().trim()
                : lead.getTitle());
    payload.put("displayName", display);
    if (c != null) {
      payload.put("email", blankTo(c.email(), lead.getEmail()));
      payload.put("phone", blankTo(c.phone(), lead.getPhone()));
      payload.put("title", c.title());
    } else {
      payload.put("email", lead.getEmail());
      payload.put("phone", lead.getPhone());
    }
    Map<String, Object> created = accountContactService.upsertContact(payload);
    return ((Number) created.get("id")).longValue();
  }

  private Long createOpportunity(CrmLeadEntity lead, Long accountId, OpportunityInput input) {
    String name =
        input != null && input.name() != null && !input.name().isBlank()
            ? input.name().trim()
            : ("Deal — " + lead.getTitle());
    OpportunityUpsert upsert =
        new OpportunityUpsert(
            name,
            lead.getId(),
            accountId,
            input == null ? null : input.pipelineId(),
            input == null ? null : input.stageId(),
            input == null ? null : input.amount(),
            input == null ? null : input.currency(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    return opportunityService.create(upsert).id();
  }

  private static String blankTo(String preferred, String fallback) {
    if (preferred != null && !preferred.isBlank()) {
      return preferred.trim();
    }
    return fallback;
  }
}
