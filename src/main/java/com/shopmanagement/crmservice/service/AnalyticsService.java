package com.shopmanagement.crmservice.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopmanagement.crmservice.persistence.entity.CrmCampaignEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmStageEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmCampaignRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmLeadRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmOpportunityRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmStageRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmTaskRepository;
import com.shopmanagement.crmservice.support.TenantIds;

@Service
public class AnalyticsService {

  private final CrmLeadRepository leadRepository;
  private final CrmOpportunityRepository opportunityRepository;
  private final CrmStageRepository stageRepository;
  private final CrmTaskRepository taskRepository;
  private final CrmCampaignRepository campaignRepository;

  public AnalyticsService(
      CrmLeadRepository leadRepository,
      CrmOpportunityRepository opportunityRepository,
      CrmStageRepository stageRepository,
      CrmTaskRepository taskRepository,
      CrmCampaignRepository campaignRepository) {
    this.leadRepository = leadRepository;
    this.opportunityRepository = opportunityRepository;
    this.stageRepository = stageRepository;
    this.taskRepository = taskRepository;
    this.campaignRepository = campaignRepository;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> summary() {
    String tenantId = TenantIds.require();
    Map<Long, String> stageNames =
        stageRepository.findByTenantIdAndDeletedAtIsNullOrderBySortOrderAsc(tenantId).stream()
            .collect(Collectors.toMap(CrmStageEntity::getId, CrmStageEntity::getName, (a, b) -> a));

    List<Map<String, Object>> leadFunnel = new ArrayList<>();
    for (Object[] row : leadRepository.countByStage(tenantId)) {
      Long stageId = (Long) row[0];
      long count = (Long) row[1];
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("stageId", stageId);
      m.put("stageName", stageNames.getOrDefault(stageId, String.valueOf(stageId)));
      m.put("count", count);
      leadFunnel.add(m);
    }

    List<Map<String, Object>> sources = new ArrayList<>();
    for (Object[] row : leadRepository.countBySource(tenantId)) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("sourceCode", row[0]);
      m.put("count", row[1]);
      sources.add(m);
    }

    Map<Long, String> campaignNames =
        campaignRepository.findByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc(tenantId).stream()
            .collect(Collectors.toMap(CrmCampaignEntity::getId, CrmCampaignEntity::getName, (a, b) -> a));

    List<Map<String, Object>> campaigns = new ArrayList<>();
    for (Object[] row : leadRepository.countByCampaign(tenantId)) {
      Long campaignId = (Long) row[0];
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("campaignId", campaignId);
      m.put("campaignName", campaignNames.getOrDefault(campaignId, String.valueOf(campaignId)));
      m.put("count", row[1]);
      campaigns.add(m);
    }

    List<Map<String, Object>> utmSources = new ArrayList<>();
    for (Object[] row : leadRepository.countByUtmSource(tenantId)) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("utmSource", row[0]);
      m.put("count", row[1]);
      utmSources.add(m);
    }

    List<Map<String, Object>> dealFunnel = new ArrayList<>();
    for (Object[] row : opportunityRepository.openFunnelByStage(tenantId)) {
      Long stageId = (Long) row[0];
      long count = (Long) row[1];
      BigDecimal amount = row[2] instanceof BigDecimal bd ? bd : BigDecimal.valueOf(((Number) row[2]).doubleValue());
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("stageId", stageId);
      m.put("stageName", stageNames.getOrDefault(stageId, String.valueOf(stageId)));
      m.put("count", count);
      m.put("amount", amount);
      dealFunnel.add(m);
    }

    Instant now = Instant.now();
    long overdueTasks =
        taskRepository.countByTenantIdAndStatusAndDueAtBeforeAndDeletedAtIsNull(tenantId, "OPEN", now);

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("leadFunnel", leadFunnel);
    out.put("leadSources", sources);
    out.put("campaigns", campaigns);
    out.put("utmSources", utmSources);
    out.put("dealFunnel", dealFunnel);
    out.put("openDeals", opportunityRepository.countByTenantIdAndStatusAndDeletedAtIsNull(tenantId, "OPEN"));
    out.put("wonDeals", opportunityRepository.countByTenantIdAndStatusAndDeletedAtIsNull(tenantId, "WON"));
    out.put("lostDeals", opportunityRepository.countByTenantIdAndStatusAndDeletedAtIsNull(tenantId, "LOST"));
    out.put("overdueTasks", overdueTasks);
    return out;
  }

  /** Sprint 5 — aging buckets, velocity, won/lost reason breakdown. */
  @Transactional(readOnly = true)
  public Map<String, Object> pipeline() {
    String tenantId = TenantIds.require();
    Instant now = Instant.now();
    Map<Long, String> stageNames =
        stageRepository.findByTenantIdAndDeletedAtIsNullOrderBySortOrderAsc(tenantId).stream()
            .collect(Collectors.toMap(CrmStageEntity::getId, CrmStageEntity::getName, (a, b) -> a));

    Map<String, Integer> agingCounts = new LinkedHashMap<>();
    agingCounts.put("0_7", 0);
    agingCounts.put("8_14", 0);
    agingCounts.put("15_30", 0);
    agingCounts.put("31_plus", 0);
    Map<String, BigDecimal> agingAmount = new LinkedHashMap<>();
    agingAmount.put("0_7", BigDecimal.ZERO);
    agingAmount.put("8_14", BigDecimal.ZERO);
    agingAmount.put("15_30", BigDecimal.ZERO);
    agingAmount.put("31_plus", BigDecimal.ZERO);

    Map<Long, long[]> stageDwell = new LinkedHashMap<>(); // [count, totalDays]
    List<Map<String, Object>> agingRows = new ArrayList<>();

    for (var o : opportunityRepository.findByTenantIdAndStatusAndDeletedAtIsNull(tenantId, "OPEN")) {
      Instant anchor = o.getUpdatedAt() != null ? o.getUpdatedAt() : o.getCreatedAt();
      long days = anchor == null ? 0 : java.time.Duration.between(anchor, now).toDays();
      String bucket = days <= 7 ? "0_7" : days <= 14 ? "8_14" : days <= 30 ? "15_30" : "31_plus";
      agingCounts.put(bucket, agingCounts.get(bucket) + 1);
      BigDecimal amt = o.getAmount() == null ? BigDecimal.ZERO : o.getAmount();
      agingAmount.put(bucket, agingAmount.get(bucket).add(amt));

      Long stageId = o.getStageId();
      long[] dwell = stageDwell.computeIfAbsent(stageId, k -> new long[2]);
      dwell[0]++;
      dwell[1] += days;

      if (agingRows.size() < 40) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", o.getId());
        row.put("name", o.getName());
        row.put("stageId", stageId);
        row.put("stageName", stageNames.getOrDefault(stageId, String.valueOf(stageId)));
        row.put("amount", amt);
        row.put("daysInStage", days);
        row.put("bucket", bucket);
        row.put("ownerUserId", o.getOwnerUserId());
        agingRows.add(row);
      }
    }

    List<Map<String, Object>> agingBuckets = new ArrayList<>();
    for (String b : List.of("0_7", "8_14", "15_30", "31_plus")) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("bucket", b);
      m.put("label", b.equals("0_7") ? "0–7 days" : b.equals("8_14") ? "8–14 days" : b.equals("15_30") ? "15–30 days" : "31+ days");
      m.put("count", agingCounts.get(b));
      m.put("amount", agingAmount.get(b));
      agingBuckets.add(m);
    }

    List<Map<String, Object>> velocityByStage = new ArrayList<>();
    for (Map.Entry<Long, long[]> e : stageDwell.entrySet()) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("stageId", e.getKey());
      m.put("stageName", stageNames.getOrDefault(e.getKey(), String.valueOf(e.getKey())));
      m.put("openCount", e.getValue()[0]);
      m.put("avgDaysInStage", e.getValue()[0] == 0 ? 0 : Math.round((double) e.getValue()[1] / e.getValue()[0] * 10) / 10.0);
      velocityByStage.add(m);
    }

    double avgWonDays = avgCycleDays(tenantId, "WON", now);
    double avgLostDays = avgCycleDays(tenantId, "LOST", now);

    List<Map<String, Object>> wonByReason = reasonBreakdown(tenantId, "WON");
    List<Map<String, Object>> lostByReason = reasonBreakdown(tenantId, "LOST");

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("agingBuckets", agingBuckets);
    out.put("agingDeals", agingRows);
    out.put("velocityByStage", velocityByStage);
    out.put("avgDaysToWon", avgWonDays);
    out.put("avgDaysToLost", avgLostDays);
    out.put("wonByReason", wonByReason);
    out.put("lostByReason", lostByReason);
    out.put(
        "counts",
        Map.of(
            "open",
            opportunityRepository.countByTenantIdAndStatusAndDeletedAtIsNull(tenantId, "OPEN"),
            "won",
            opportunityRepository.countByTenantIdAndStatusAndDeletedAtIsNull(tenantId, "WON"),
            "lost",
            opportunityRepository.countByTenantIdAndStatusAndDeletedAtIsNull(tenantId, "LOST")));
    return out;
  }

  private double avgCycleDays(String tenantId, String status, Instant now) {
    List<com.shopmanagement.crmservice.persistence.entity.CrmOpportunityEntity> closed =
        opportunityRepository.findByTenantIdAndStatusAndDeletedAtIsNull(tenantId, status);
    if (closed.isEmpty()) {
      return 0;
    }
    long total = 0;
    int n = 0;
    for (var o : closed) {
      Instant start = o.getCreatedAt();
      Instant end = o.getUpdatedAt() != null ? o.getUpdatedAt() : now;
      if (start == null) {
        continue;
      }
      total += java.time.Duration.between(start, end).toDays();
      n++;
    }
    return n == 0 ? 0 : Math.round((double) total / n * 10) / 10.0;
  }

  private List<Map<String, Object>> reasonBreakdown(String tenantId, String status) {
    List<Map<String, Object>> rows = new ArrayList<>();
    for (Object[] row : opportunityRepository.countClosedByReason(tenantId, status)) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("reasonCode", row[0]);
      m.put("count", row[1]);
      m.put(
          "amount",
          row[2] instanceof BigDecimal bd ? bd : BigDecimal.valueOf(((Number) row[2]).doubleValue()));
      rows.add(m);
    }
    return rows;
  }
}
