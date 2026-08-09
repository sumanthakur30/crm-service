package com.shopmanagement.crmservice.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Sprint 7 — ad-hoc omnichannel send (WA / SMS / Email). */
public final class CrmMessageApi {

  private CrmMessageApi() {}

  public record SendRequest(
      @NotNull Long leadId,
      @NotBlank @Size(max = 16) String channel,
      @Size(max = 256) String recipient,
      @Size(max = 256) String subject,
      @NotBlank String body,
      @Size(max = 64) String templateCode) {}
}
