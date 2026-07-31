package com.shopmanagement.crmservice.cti;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "crm.cti.provider", havingValue = "STUB", matchIfMissing = true)
public class StubCtiClient implements CtiClient {

  private static final Logger log = LoggerFactory.getLogger(StubCtiClient.class);

  @Override
  public Map<String, Object> clickToDial(String tenantId, String phone, Map<String, Object> context) {
    String callId = "stub-" + UUID.randomUUID();
    log.info(
        "CTI STUB click-to-dial tenant={} phone={} callId={} context={}",
        tenantId,
        phone,
        callId,
        context);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("status", "STUB");
    out.put("callId", callId);
    out.put("provider", "STUB");
    out.put("phone", phone);
    return out;
  }

  @Override
  public Map<String, Object> inboundWebhook(String tenantId, Map<String, Object> payload) {
    log.info("CTI STUB inbound webhook tenant={} payload={}", tenantId, payload);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("status", "STUB");
    out.put("accepted", true);
    return out;
  }
}
