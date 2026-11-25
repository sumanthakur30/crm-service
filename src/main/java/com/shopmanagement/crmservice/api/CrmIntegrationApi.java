package com.shopmanagement.crmservice.api;

import java.time.Instant;
import java.util.List;

public final class CrmIntegrationApi {

  private CrmIntegrationApi() {}

  public record IntegrationCard(
      String provider,
      String status,
      String displayName,
      String summary,
      int selectedAssets,
      List<String> assetNames,
      Instant lastSuccessAt,
      long leadsLast7Days,
      long failedCount,
      String lastError,
      String reconnect) {}
}
