package com.shopmanagement.crmservice.cti;

import java.util.Map;

/** Minimal CTI / softphone SPI — plug real telephony later. */
public interface CtiClient {

  Map<String, Object> clickToDial(String tenantId, String phone, Map<String, Object> context);

  /** Optional inbound webhook stub for future softphone events. */
  default Map<String, Object> inboundWebhook(String tenantId, Map<String, Object> payload) {
    return Map.of("status", "IGNORED", "message", "Inbound CTI webhook not implemented");
  }
}
