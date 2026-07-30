package com.shopmanagement.crmservice.entitlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shopmanagement.crmservice.config.CrmProperties;
import com.shopmanagement.crmservice.filter.TenantContextFilter;

@ExtendWith(MockitoExtension.class)
class CrmEntitlementGuardTest {

  @Mock private SubscriptionEntitlementClient client;

  private CrmProperties properties;
  private CrmEntitlementGuard guard;

  @BeforeEach
  void setUp() {
    properties = new CrmProperties();
    properties.setEnabled(true);
    guard = new CrmEntitlementGuard(properties, client);
    TenantContextFilter.bindTenantForTests("tenant-demo");
  }

  @AfterEach
  void tearDown() {
    TenantContextFilter.clearTenantForTests();
  }

  @Test
  void requireCampaignAccess_blocksWhenFlagMissing() {
    when(client.hasFeature("tenant-demo", "FEATURE_CRM")).thenReturn(true);
    when(client.hasFeature("tenant-demo", "FEATURE_CRM_CAMPAIGN")).thenReturn(false);

    assertThatThrownBy(() -> guard.requireCampaignAccess())
        .isInstanceOf(CrmEntitlementException.class)
        .hasMessageContaining("FEATURE_CRM_CAMPAIGN");
  }

  @Test
  void requireChannelAccess_whatsapp() {
    when(client.hasFeature("tenant-demo", "FEATURE_CRM_WHATSAPP")).thenReturn(false);

    assertThatThrownBy(() -> guard.requireChannelAccess("WHATSAPP"))
        .isInstanceOf(CrmEntitlementException.class)
        .hasMessageContaining("FEATURE_CRM_WHATSAPP");
  }

  @Test
  void entitlementsSnapshot_whenChecksOff_allEnabled() {
    properties.setEnabled(false);
    Map<String, Object> snap = guard.entitlementsSnapshot();

    assertThat(snap.get("checksEnabled")).isEqualTo(false);
    @SuppressWarnings("unchecked")
    Map<String, Boolean> modules = (Map<String, Boolean>) snap.get("modules");
    assertThat(modules.get("quotes")).isTrue();
    assertThat(modules.get("campaigns")).isTrue();
    assertThat(modules.get("ai")).isTrue();
    assertThat(modules.get("sequences")).isTrue();
  }

  @Test
  void entitlementsSnapshot_whenChecksOn_reflectsClient() {
    when(client.hasFeature("tenant-demo", "FEATURE_CRM")).thenReturn(true);
    when(client.hasFeature("tenant-demo", "FEATURE_CRM_QUOTE")).thenReturn(true);
    when(client.hasFeature("tenant-demo", "FEATURE_CRM_CAMPAIGN")).thenReturn(false);
    when(client.hasFeature("tenant-demo", "FEATURE_CRM_AI")).thenReturn(false);
    when(client.hasFeature("tenant-demo", "FEATURE_CRM_SEQUENCES")).thenReturn(true);
    when(client.hasFeature("tenant-demo", "FEATURE_CRM_WHATSAPP")).thenReturn(true);
    when(client.hasFeature("tenant-demo", "FEATURE_CRM_SMS")).thenReturn(true);
    when(client.hasFeature("tenant-demo", "FEATURE_CRM_EMAIL")).thenReturn(true);
    when(client.hasFeature("tenant-demo", "FEATURE_CRM_APPROVAL")).thenReturn(true);
    when(client.hasFeature("tenant-demo", "FEATURE_CRM_AUTOMATION")).thenReturn(true);
    when(client.hasFeature("tenant-demo", "FEATURE_CRM_API")).thenReturn(true);

    Map<String, Object> snap = guard.entitlementsSnapshot();
    @SuppressWarnings("unchecked")
    Map<String, Boolean> modules = (Map<String, Boolean>) snap.get("modules");
    assertThat(modules.get("quotes")).isTrue();
    assertThat(modules.get("campaigns")).isFalse();
    assertThat(modules.get("ai")).isFalse();
    assertThat(modules.get("sequences")).isTrue();
  }
}
