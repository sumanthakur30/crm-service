package com.shopmanagement.crmservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.shopmanagement.crmservice.config.CrmCtiProperties;
import com.shopmanagement.crmservice.cti.CtiClient;
import com.shopmanagement.crmservice.filter.TenantContextFilter;

@ExtendWith(MockitoExtension.class)
class CtiServiceTest {

  @Mock private CtiClient ctiClient;
  @Mock private TimelineService timelineService;

  private CrmCtiProperties properties;
  private CtiService service;

  @BeforeEach
  void setUp() {
    properties = new CrmCtiProperties();
    properties.setEnabled(true);
    properties.setProvider("STUB");
    service = new CtiService(properties, ctiClient, timelineService);
    TenantContextFilter.bindTenantForTests("demo-crm");
  }

  @AfterEach
  void tearDown() {
    TenantContextFilter.clearTenantForTests();
  }

  @Test
  void clickToDialWhenDisabledReturns503() {
    properties.setEnabled(false);
    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class,
            () -> service.clickToDial(Map.of("phone", "9876543210")));
    assertEquals(503, ex.getStatusCode().value());
  }

  @Test
  void clickToDialRecordsTimelineWhenLeadPresent() {
    when(ctiClient.clickToDial(eq("demo-crm"), eq("9876543210"), any()))
        .thenReturn(Map.of("status", "STUB", "callId", "stub-1"));

    Map<String, Object> result =
        service.clickToDial(Map.of("phone", "9876543210", "leadId", 44));

    assertEquals("STUB", result.get("status"));
    assertEquals("stub-1", result.get("callId"));
    verify(timelineService)
        .recordEvent(
            eq("LEAD"),
            eq(44L),
            eq("CTI_CLICK_TO_DIAL"),
            eq("CTI click-to-dial 9876543210"),
            any());
  }

  @Test
  void clickToDialRequiresPhone() {
    ResponseStatusException ex =
        assertThrows(ResponseStatusException.class, () -> service.clickToDial(Map.of()));
    assertEquals(400, ex.getStatusCode().value());
    assertTrue(ex.getReason().contains("phone"));
  }
}
