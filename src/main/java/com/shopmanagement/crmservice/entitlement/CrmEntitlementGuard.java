package com.shopmanagement.crmservice.entitlement;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.shopmanagement.crmservice.config.CrmProperties;
import com.shopmanagement.crmservice.support.TenantIds;

@Component
public class CrmEntitlementGuard {

  private final CrmProperties properties;
  private final SubscriptionEntitlementClient client;

  public CrmEntitlementGuard(CrmProperties properties, SubscriptionEntitlementClient client) {
    this.properties = properties;
    this.client = client;
  }

  public void requireCrmAccess() {
    requireFlag(properties.getFlag(), "assign a CRM plan (crm-starter or higher)");
  }

  /** Quote APIs — FEATURE_CRM + FEATURE_CRM_QUOTE. */
  public void requireQuoteAccess() {
    requireCrmAccess();
    requireSubFlag(properties.getQuoteFlag(), "assign crm-professional or higher for quotations");
  }

  public void requireCampaignAccess() {
    requireCrmAccess();
    requireSubFlag(properties.getCampaignFlag(), "assign crm-enterprise for campaigns");
  }

  public void requireAiAccess() {
    requireCrmAccess();
    requireSubFlag(properties.getAiFlag(), "assign crm-enterprise for CRM AI");
  }

  public void requireSequencesAccess() {
    requireCrmAccess();
    requireSubFlag(properties.getSequencesFlag(), "assign crm-professional or higher for sequences");
  }

  public void requireApprovalAccess() {
    requireCrmAccess();
    requireSubFlag(properties.getApprovalFlag(), "assign crm-professional or higher for approvals");
  }

  public void requireAutomationAccess() {
    requireCrmAccess();
    requireSubFlag(properties.getAutomationFlag(), "assign crm-professional or higher for automation");
  }

  public void requireApiAccess() {
    requireCrmAccess();
    requireSubFlag(properties.getApiFlag(), "assign crm-professional or higher for CRM API / ingest");
  }

  /** Cases APIs — FEATURE_CRM + FEATURE_CRM_CASES (enterprise). */
  public void requireCasesAccess() {
    requireCrmAccess();
    requireSubFlag(properties.getCasesFlag(), "assign crm-enterprise for Cases / CSAT");
  }

  /** Channel gate for quote send / sequence dispatch (WHATSAPP / SMS / EMAIL). */
  public void requireChannelAccess(String channel) {
    if (channel == null || channel.isBlank()) {
      return;
    }
    String c = channel.trim().toUpperCase();
    switch (c) {
      case "WHATSAPP" ->
          requireSubFlag(properties.getWhatsappFlag(), "assign a plan with FEATURE_CRM_WHATSAPP");
      case "SMS" -> requireSubFlag(properties.getSmsFlag(), "assign a plan with FEATURE_CRM_SMS");
      case "EMAIL" -> requireSubFlag(properties.getEmailFlag(), "assign a plan with FEATURE_CRM_EMAIL");
      default -> {
        /* unknown channel — leave to service validation */
      }
    }
  }

  /**
   * Snapshot for crm-ui tab gating. When entitlement checks are off (local), all gated features
   * report enabled=true so the pilot UI stays fully usable.
   */
  public Map<String, Object> entitlementsSnapshot() {
    boolean checksOn = properties.isEnabled();
    Map<String, Boolean> features = new LinkedHashMap<>();
    String tenantId = null;
    if (checksOn) {
      tenantId = TenantIds.require();
    }
    putFeature(features, properties.getFlag(), tenantId, checksOn);
    putFeature(features, properties.getQuoteFlag(), tenantId, checksOn);
    putFeature(features, properties.getCampaignFlag(), tenantId, checksOn);
    putFeature(features, properties.getAiFlag(), tenantId, checksOn);
    putFeature(features, properties.getSequencesFlag(), tenantId, checksOn);
    putFeature(features, properties.getWhatsappFlag(), tenantId, checksOn);
    putFeature(features, properties.getSmsFlag(), tenantId, checksOn);
    putFeature(features, properties.getEmailFlag(), tenantId, checksOn);
    putFeature(features, properties.getApprovalFlag(), tenantId, checksOn);
    putFeature(features, properties.getAutomationFlag(), tenantId, checksOn);
    putFeature(features, properties.getApiFlag(), tenantId, checksOn);
    putFeature(features, properties.getCasesFlag(), tenantId, checksOn);

    Map<String, Boolean> modules = new LinkedHashMap<>();
    modules.put("leads", Boolean.TRUE.equals(features.get(properties.getFlag())));
    modules.put("quotes", Boolean.TRUE.equals(features.get(properties.getQuoteFlag())));
    modules.put("campaigns", Boolean.TRUE.equals(features.get(properties.getCampaignFlag())));
    modules.put("ai", Boolean.TRUE.equals(features.get(properties.getAiFlag())));
    modules.put("sequences", Boolean.TRUE.equals(features.get(properties.getSequencesFlag())));
    modules.put("approvals", Boolean.TRUE.equals(features.get(properties.getApprovalFlag())));
    modules.put("automation", Boolean.TRUE.equals(features.get(properties.getAutomationFlag())));
    modules.put("ops", Boolean.TRUE.equals(features.get(properties.getFlag())));
    modules.put("cases", Boolean.TRUE.equals(features.get(properties.getCasesFlag())));

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("checksEnabled", checksOn);
    out.put("features", features);
    out.put("modules", modules);
    return out;
  }

  private void putFeature(Map<String, Boolean> features, String flag, String tenantId, boolean checksOn) {
    if (flag == null || flag.isBlank()) {
      return;
    }
    String code = flag.trim();
    if (!checksOn) {
      features.put(code, true);
      return;
    }
    features.put(code, client.hasFeature(tenantId, code));
  }

  private void requireFlag(String flag, String hint) {
    if (!properties.isEnabled()) {
      return;
    }
    String code = flag == null ? "" : flag.trim();
    if (code.isBlank()) {
      return;
    }
    String tenantId = TenantIds.require();
    if (!client.hasFeature(tenantId, code)) {
      throw new CrmEntitlementException(code + " is not enabled for this tenant — " + hint);
    }
  }

  private void requireSubFlag(String flag, String hint) {
    if (!properties.isEnabled()) {
      return;
    }
    if (flag == null || flag.isBlank()) {
      return;
    }
    requireFlag(flag, hint);
  }
}
