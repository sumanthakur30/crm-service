package com.shopmanagement.crmservice.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopmanagement.crmservice.integration.OrderClient;
import com.shopmanagement.crmservice.persistence.entity.CrmAccountEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmLeadEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmOpportunityEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmQuotationEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmAccountRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmLeadRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmOpportunityRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmQuotationRepository;
import com.shopmanagement.crmservice.support.TenantIds;

/**
 * Sprint 3 — read-only Customer 360 ERP strip. Prefer live order-service reads; always include
 * CRM quote-linked order/payment projections. No duplicate ERP tables in CRM.
 */
@Service
public class ErpFederationService {

  private final CrmAccountRepository accountRepository;
  private final CrmLeadRepository leadRepository;
  private final CrmOpportunityRepository opportunityRepository;
  private final CrmQuotationRepository quotationRepository;
  private final OrderClient orderClient;

  public ErpFederationService(
      CrmAccountRepository accountRepository,
      CrmLeadRepository leadRepository,
      CrmOpportunityRepository opportunityRepository,
      CrmQuotationRepository quotationRepository,
      OrderClient orderClient) {
    this.accountRepository = accountRepository;
    this.leadRepository = leadRepository;
    this.opportunityRepository = opportunityRepository;
    this.quotationRepository = quotationRepository;
    this.orderClient = orderClient;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> accountErpStrip(Long accountId) {
    String tenantId = TenantIds.require();
    CrmAccountEntity account =
        accountRepository
            .findByTenantIdAndIdAndDeletedAtIsNull(tenantId, accountId)
            .orElse(null);
    Map<String, Object> out = new LinkedHashMap<>();
    if (account == null) {
      out.put("status", "ACCOUNT_NOT_FOUND");
      out.put("orders", List.of());
      out.put("invoices", List.of());
      out.put("payments", List.of());
      return out;
    }

    Long shopCustomerId = resolveShopCustomerId(tenantId, account);
    out.put("shopCustomerId", shopCustomerId);
    out.put("orderEnabled", orderClient.isEnabled());

    List<CrmOpportunityEntity> opps =
        opportunityRepository.findByTenantIdAndAccountIdAndDeletedAtIsNull(tenantId, accountId);
    List<CrmQuotationEntity> quotes = new ArrayList<>();
    for (CrmOpportunityEntity opp : opps) {
      quotes.addAll(
          quotationRepository.findByTenantIdAndOpportunityIdAndDeletedAtIsNullOrderByVersionNoDesc(
              tenantId, opp.getId()));
    }

    List<Map<String, Object>> crmOrders = new ArrayList<>();
    List<Map<String, Object>> invoices = new ArrayList<>();
    List<Map<String, Object>> payments = new ArrayList<>();
    Set<String> seenOrderKeys = new LinkedHashSet<>();

    for (CrmQuotationEntity q : quotes) {
      Map<String, Object> share =
          q.getSharePayloadJson() == null ? Map.of() : q.getSharePayloadJson();
      Object orderId = share.get("orderId");
      if (orderId != null && !String.valueOf(orderId).isBlank()) {
        String key = String.valueOf(orderId);
        if (seenOrderKeys.add(key)) {
          Map<String, Object> row = new LinkedHashMap<>();
          row.put("source", "CRM_QUOTE");
          row.put("orderId", orderId);
          row.put("orderNumber", share.get("orderNumber"));
          row.put("quotationId", q.getId());
          row.put("quoteNumber", q.getQuoteNumber());
          row.put("amount", q.getTotalAmount());
          row.put("status", q.getStatus());
          Object create = share.get("orderCreate");
          if (create instanceof Map<?, ?> m) {
            row.put("createStatus", m.get("status"));
          }
          crmOrders.add(row);
        }
      }

      if ("ACCEPTED".equalsIgnoreCase(q.getStatus())
          || "SENT".equalsIgnoreCase(q.getStatus())
          || "ACCEPTED".equalsIgnoreCase(String.valueOf(q.getStatus()))) {
        Map<String, Object> inv = new LinkedHashMap<>();
        inv.put("source", "CRM_QUOTE");
        inv.put("quotationId", q.getId());
        inv.put("quoteNumber", q.getQuoteNumber());
        inv.put("status", q.getStatus());
        inv.put("amount", q.getTotalAmount());
        inv.put("currency", q.getCurrency());
        inv.put("note", "Commercial quote (ERP invoice federation pending)");
        invoices.add(inv);
      }

      if (q.getPaymentStatus() != null && !"NONE".equalsIgnoreCase(q.getPaymentStatus())) {
        Map<String, Object> pay = new LinkedHashMap<>();
        pay.put("source", "CRM_QUOTE");
        pay.put("quotationId", q.getId());
        pay.put("quoteNumber", q.getQuoteNumber());
        pay.put("paymentStatus", q.getPaymentStatus());
        pay.put("paymentProvider", q.getPaymentProvider());
        pay.put("paymentRef", q.getPaymentRef());
        pay.put("paymentAmount", q.getPaymentAmount());
        pay.put("paymentLinkUrl", q.getPaymentLinkUrl());
        payments.add(pay);
      }
    }

    List<Map<String, Object>> liveOrders = new ArrayList<>();
    String liveStatus = "SKIPPED_DISABLED";
    if (!orderClient.isEnabled()) {
      liveStatus = "SKIPPED_DISABLED";
    } else if (shopCustomerId == null) {
      liveStatus = "SKIPPED_UNMAPPED";
    } else {
      Map<String, Object> fetched = orderClient.listByCustomer(tenantId, shopCustomerId, 20);
      liveStatus = String.valueOf(fetched.getOrDefault("status", "ERROR"));
      Object rows = fetched.get("orders");
      if (rows instanceof List<?> list) {
        for (Object row : list) {
          if (row instanceof Map<?, ?> map) {
            Map<String, Object> m = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
              m.put(String.valueOf(e.getKey()), e.getValue());
            }
            m.putIfAbsent("source", "ORDER_SERVICE");
            liveOrders.add(m);
            Object id = m.get("id") != null ? m.get("id") : m.get("orderId");
            if (id != null) {
              seenOrderKeys.add(String.valueOf(id));
            }
          }
        }
      }
      // Enrich known quote order ids that aren't in the list
      for (Map<String, Object> crm : crmOrders) {
        Object oid = crm.get("orderId");
        if (oid == null) {
          continue;
        }
        boolean already =
            liveOrders.stream()
                .anyMatch(
                    o ->
                        String.valueOf(oid).equals(String.valueOf(o.get("id")))
                            || String.valueOf(oid).equals(String.valueOf(o.get("orderId"))));
        if (!already) {
          Map<String, Object> one = orderClient.getOrder(tenantId, oid);
          if ("OK".equals(one.get("status")) && one.get("order") instanceof Map<?, ?> om) {
            Map<String, Object> m = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : om.entrySet()) {
              m.put(String.valueOf(e.getKey()), e.getValue());
            }
            m.put("source", "ORDER_SERVICE");
            liveOrders.add(m);
          }
        }
      }
    }

    List<Map<String, Object>> orders = new ArrayList<>();
    orders.addAll(liveOrders);
    for (Map<String, Object> crm : crmOrders) {
      Object oid = crm.get("orderId");
      boolean already =
          liveOrders.stream()
              .anyMatch(
                  o ->
                      oid != null
                          && (String.valueOf(oid).equals(String.valueOf(o.get("id")))
                              || String.valueOf(oid).equals(String.valueOf(o.get("orderId")))));
      if (!already) {
        orders.add(crm);
      }
    }

    out.put("liveStatus", liveStatus);
    out.put("orders", orders);
    out.put("invoices", invoices);
    out.put("payments", payments);
    if (!orderClient.isEnabled()) {
      out.put("status", "CRM_ONLY");
    } else if (shopCustomerId == null) {
      out.put("status", "UNMAPPED");
    } else if ("ERROR".equals(liveStatus)) {
      out.put("status", "DEGRADED");
    } else {
      out.put("status", "FEDERATED");
    }
    return out;
  }

  /** Prefer account attribute, then linked lead SHOP_CUSTOMER externalId, then order default. */
  public Long resolveShopCustomerId(String tenantId, CrmAccountEntity account) {
    if (account.getAttributes() != null) {
      Long fromAttr = asLong(account.getAttributes().get("shopCustomerId"));
      if (fromAttr == null) {
        fromAttr = asLong(account.getAttributes().get("SHOP_CUSTOMER_ID"));
      }
      if (fromAttr != null) {
        return fromAttr;
      }
    }
    for (CrmLeadEntity lead :
        leadRepository.findByTenantIdAndAccountIdAndDeletedAtIsNull(tenantId, account.getId())) {
      Long fromLead = shopCustomerFromRefs(lead.getExternalRefs());
      if (fromLead != null) {
        return fromLead;
      }
    }
    return orderClient.properties().getDefaultCustomerId();
  }

  @Transactional
  public void stampShopCustomerOnAccount(Long accountId, Long shopCustomerId) {
    if (accountId == null || shopCustomerId == null) {
      return;
    }
    String tenantId = TenantIds.require();
    accountRepository
        .findByTenantIdAndIdAndDeletedAtIsNull(tenantId, accountId)
        .ifPresent(
            account -> {
              Map<String, Object> attrs =
                  new LinkedHashMap<>(
                      account.getAttributes() == null ? Map.of() : account.getAttributes());
              attrs.put("shopCustomerId", shopCustomerId);
              account.setAttributes(attrs);
              account.touch();
              accountRepository.save(account);
            });
  }

  static Long shopCustomerFromRefs(Map<String, Object> refs) {
    if (refs == null) {
      return null;
    }
    Object entry = refs.get("SHOP_CUSTOMER");
    if (!(entry instanceof Map<?, ?> map)) {
      return null;
    }
    return asLong(map.get("externalId"));
  }

  private static Long asLong(Object v) {
    if (v == null) {
      return null;
    }
    if (v instanceof Number n) {
      return n.longValue();
    }
    try {
      return Long.parseLong(String.valueOf(v).trim());
    } catch (NumberFormatException ex) {
      return null;
    }
  }
}
