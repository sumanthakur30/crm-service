package com.shopmanagement.crmservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.shopmanagement.crmservice.config.CrmConvertProperties;
import com.shopmanagement.crmservice.filter.TenantContextFilter;
import com.shopmanagement.crmservice.persistence.entity.CrmConvertEventEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmLeadEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmConvertEventRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmLeadRepository;

@ExtendWith(MockitoExtension.class)
class LeadConvertServiceTest {

  @Mock private CrmLeadRepository leadRepository;
  @Mock private CrmConvertEventRepository convertEventRepository;
  @Mock private RestTemplate restTemplate;
  @Mock private TimelineService timelineService;
  @Mock private ErpFederationService erpFederationService;

  private CrmConvertProperties properties;
  private LeadConvertService service;

  @BeforeEach
  void setUp() {
    properties = new CrmConvertProperties();
    properties.setEnabled(true);
    properties.setShopCustomerUrl("http://localhost:8095/api/v1/crm/adapters/erp/SHOP_CUSTOMER");
    service =
        new LeadConvertService(
            leadRepository,
            convertEventRepository,
            properties,
            restTemplate,
            timelineService,
            erpFederationService);
    TenantContextFilter.bindTenantForTests("42");
    TenantContextFilter.bindShopForTests("99");
  }

  @AfterEach
  void tearDown() {
    TenantContextFilter.clearTenantForTests();
  }

  @Test
  void convert_idempotentWhenAlreadySent() {
    CrmLeadEntity lead = baseLead();
    Map<String, Object> refs = new LinkedHashMap<>();
    refs.put(
        "SHOP_CUSTOMER",
        Map.of("eventId", 7L, "status", "SENT", "externalId", "CUST-1"));
    lead.setExternalRefs(refs);
    when(leadRepository.findByTenantIdAndIdAndDeletedAtIsNull("42", 1L)).thenReturn(Optional.of(lead));

    Map<String, Object> result = service.convert(1L, "SHOP_CUSTOMER");

    assertThat(result.get("status")).isEqualTo("ACKED");
    assertThat(result.get("alreadyConverted")).isEqualTo(true);
    assertThat(result.get("externalId")).isEqualTo("CUST-1");
    verify(convertEventRepository, never()).save(any());
    verify(restTemplate, never())
        .exchange(
            any(String.class),
            any(HttpMethod.class),
            any(HttpEntity.class),
            any(org.springframework.core.ParameterizedTypeReference.class));
  }

  @Test
  void convert_setsConvertedAndUsesShopHeader() {
    CrmLeadEntity lead = baseLead();
    when(leadRepository.findByTenantIdAndIdAndDeletedAtIsNull("42", 1L)).thenReturn(Optional.of(lead));
    when(convertEventRepository.save(any(CrmConvertEventEntity.class)))
        .thenAnswer(
            inv -> {
              CrmConvertEventEntity e = inv.getArgument(0);
              e.setId(55L);
              return e;
            });
    when(leadRepository.save(any(CrmLeadEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    when(restTemplate.exchange(
            any(String.class),
            any(HttpMethod.class),
            any(HttpEntity.class),
            any(org.springframework.core.ParameterizedTypeReference.class)))
        .thenReturn(ResponseEntity.ok(Map.of("id", "CRM-SINK-1", "sink", true)));

    Map<String, Object> result = service.convert(1L, "shop_customer");

    assertThat(result.get("status")).isEqualTo("SENT");
    assertThat(result.get("mode")).isEqualTo("SINK");
    assertThat(result.get("externalId")).isEqualTo("CRM-SINK-1");
    assertThat(lead.getStatus()).isEqualTo("CONVERTED");

    @SuppressWarnings("unchecked")
    ArgumentCaptor<HttpEntity<Map<String, Object>>> captor = ArgumentCaptor.forClass(HttpEntity.class);
    verify(restTemplate)
        .exchange(
            any(String.class),
            any(HttpMethod.class),
            captor.capture(),
            any(org.springframework.core.ParameterizedTypeReference.class));
    assertThat(captor.getValue().getHeaders().getFirst("X-Shop-Id")).isEqualTo("99");
  }

  private static CrmLeadEntity baseLead() {
    CrmLeadEntity lead = new CrmLeadEntity();
    lead.setId(1L);
    lead.setTenantId("42");
    lead.setPipelineId(1L);
    lead.setStageId(1L);
    lead.setTitle("Acme");
    lead.setDisplayName("Ravi");
    lead.setPhone("9999999999");
    lead.setStatus("OPEN");
    lead.setExternalRefs(new LinkedHashMap<>());
    return lead;
  }
}
