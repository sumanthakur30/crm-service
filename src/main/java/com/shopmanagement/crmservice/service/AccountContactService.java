package com.shopmanagement.crmservice.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.shopmanagement.crmservice.persistence.entity.CrmAccountEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmContactEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmLeadEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmOpportunityEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmAccountRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmContactRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmLeadRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmOpportunityRepository;
import com.shopmanagement.crmservice.support.TenantIds;

@Service
public class AccountContactService {

  private final CrmAccountRepository accountRepository;
  private final CrmContactRepository contactRepository;
  private final CrmLeadRepository leadRepository;
  private final CrmOpportunityRepository opportunityRepository;

  public AccountContactService(
      CrmAccountRepository accountRepository,
      CrmContactRepository contactRepository,
      CrmLeadRepository leadRepository,
      CrmOpportunityRepository opportunityRepository) {
    this.accountRepository = accountRepository;
    this.contactRepository = contactRepository;
    this.leadRepository = leadRepository;
    this.opportunityRepository = opportunityRepository;
  }

  @Transactional
  public Map<String, Object> upsertAccount(Map<String, Object> body) {
    String tenantId = TenantIds.require();
    Long id = body.get("id") == null ? null : asLong(body.get("id"));
    CrmAccountEntity account;
    if (id != null) {
      account =
          accountRepository
              .findByTenantIdAndIdAndDeletedAtIsNull(tenantId, id)
              .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
    } else {
      account = new CrmAccountEntity();
      account.setTenantId(tenantId);
    }
    account.setName(req(body, "name"));
    account.setGstin(str(body.get("gstin")));
    account.setPhone(str(body.get("phone")));
    account.setEmail(str(body.get("email")));
    account.setStateCode(str(body.get("stateCode")));
    account.setPincode(str(body.get("pincode")));
    if (body.get("attributes") instanceof Map<?, ?> attrs) {
      account.setAttributes(castMap(attrs));
    } else if (account.getAttributes() == null) {
      account.setAttributes(new LinkedHashMap<>());
    }
    account.touch();
    return toAccount(accountRepository.save(account));
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listAccounts() {
    return accountRepository
        .findByTenantIdAndDeletedAtIsNullOrderByNameAsc(TenantIds.require())
        .stream()
        .map(AccountContactService::toAccount)
        .toList();
  }

  @Transactional(readOnly = true)
  public Map<String, Object> getAccount(Long id) {
    return toAccount(
        accountRepository
            .findByTenantIdAndIdAndDeletedAtIsNull(TenantIds.require(), id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found")));
  }

  @Transactional
  public Map<String, Object> upsertContact(Map<String, Object> body) {
    String tenantId = TenantIds.require();
    Long id = body.get("id") == null ? null : asLong(body.get("id"));
    CrmContactEntity contact;
    if (id != null) {
      contact =
          contactRepository
              .findByTenantIdAndIdAndDeletedAtIsNull(tenantId, id)
              .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contact not found"));
    } else {
      contact = new CrmContactEntity();
      contact.setTenantId(tenantId);
    }
    Long accountId = body.get("accountId") == null ? null : asLong(body.get("accountId"));
    if (accountId != null) {
      accountRepository
          .findByTenantIdAndIdAndDeletedAtIsNull(tenantId, accountId)
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
    }
    contact.setAccountId(accountId);
    contact.setDisplayName(req(body, "displayName"));
    contact.setEmail(str(body.get("email")));
    contact.setPhone(str(body.get("phone")));
    contact.setTitle(str(body.get("title")));
    if (body.get("attributes") instanceof Map<?, ?> attrs) {
      contact.setAttributes(castMap(attrs));
    } else if (contact.getAttributes() == null) {
      contact.setAttributes(new LinkedHashMap<>());
    }
    contact.touch();
    return toContact(contactRepository.save(contact));
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listContacts(Long accountId) {
    String tenantId = TenantIds.require();
    List<CrmContactEntity> contacts =
        accountId == null
            ? contactRepository.findByTenantIdAndDeletedAtIsNullOrderByDisplayNameAsc(tenantId)
            : contactRepository.findByTenantIdAndAccountIdAndDeletedAtIsNull(tenantId, accountId);
    return contacts.stream().map(AccountContactService::toContact).toList();
  }

  @Transactional(readOnly = true)
  public Map<String, Object> getContact(Long id) {
    return toContact(
        contactRepository
            .findByTenantIdAndIdAndDeletedAtIsNull(TenantIds.require(), id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contact not found")));
  }

  /** Account detail aggregate for CRM UI drawer. */
  @Transactional(readOnly = true)
  public Map<String, Object> accountSummary(Long accountId) {
    String tenantId = TenantIds.require();
    Map<String, Object> account = getAccount(accountId);
    List<Map<String, Object>> contacts = listContacts(accountId);
    List<Map<String, Object>> leads = new java.util.ArrayList<>();
    for (CrmLeadEntity lead :
        leadRepository.findByTenantIdAndAccountIdAndDeletedAtIsNull(tenantId, accountId)) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("id", lead.getId());
      row.put("title", lead.getTitle());
      row.put("status", lead.getStatus());
      row.put("ownerUserId", lead.getOwnerUserId());
      row.put("phone", lead.getPhone());
      leads.add(row);
    }
    List<Map<String, Object>> opportunities = new java.util.ArrayList<>();
    for (CrmOpportunityEntity opp :
        opportunityRepository.findByTenantIdAndAccountIdAndDeletedAtIsNull(tenantId, accountId)) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("id", opp.getId());
      row.put("name", opp.getName());
      row.put("status", opp.getStatus());
      row.put("amount", opp.getAmount());
      row.put("stageId", opp.getStageId());
      opportunities.add(row);
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("account", account);
    out.put("contacts", contacts);
    out.put("leads", leads);
    out.put("opportunities", opportunities);
    return out;
  }

  private static Map<String, Object> toAccount(CrmAccountEntity a) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", a.getId());
    m.put("name", a.getName());
    m.put("gstin", a.getGstin());
    m.put("phone", a.getPhone());
    m.put("email", a.getEmail());
    m.put("stateCode", a.getStateCode());
    m.put("pincode", a.getPincode());
    m.put("attributes", a.getAttributes());
    m.put("createdAt", a.getCreatedAt());
    m.put("updatedAt", a.getUpdatedAt());
    return m;
  }

  private static Map<String, Object> toContact(CrmContactEntity c) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", c.getId());
    m.put("accountId", c.getAccountId());
    m.put("displayName", c.getDisplayName());
    m.put("email", c.getEmail());
    m.put("phone", c.getPhone());
    m.put("title", c.getTitle());
    m.put("attributes", c.getAttributes());
    m.put("createdAt", c.getCreatedAt());
    m.put("updatedAt", c.getUpdatedAt());
    return m;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> castMap(Map<?, ?> attrs) {
    return new LinkedHashMap<>((Map<String, Object>) attrs);
  }

  private static String req(Map<String, Object> body, String key) {
    Object v = body.get(key);
    if (v == null || String.valueOf(v).isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " required");
    }
    return String.valueOf(v).trim();
  }

  private static String str(Object v) {
    return v == null || String.valueOf(v).isBlank() ? null : String.valueOf(v).trim();
  }

  private static Long asLong(Object v) {
    if (v == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id required");
    }
    return ((Number) v).longValue();
  }
}
