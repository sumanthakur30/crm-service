package com.shopmanagement.crmservice.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopmanagement.crmservice.filter.TenantContextFilter;
import com.shopmanagement.crmservice.persistence.entity.CrmCampaignEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmLeadEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmOpportunityEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmStageEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmTaskEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmCampaignRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmLeadRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmOpportunityRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmStageRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmTaskRepository;
import com.shopmanagement.crmservice.security.CrmAccessScope;
import com.shopmanagement.crmservice.security.CrmRecordScopeService;
import com.shopmanagement.crmservice.security.CrmRecordScopeService.ScopeListFilter;
import com.shopmanagement.crmservice.support.TenantIds;

@Service
public class AnalyticsService {

  private final CrmLeadRepository leadRepository;
  private final CrmOpportunityRepository opportunityRepository;
  private final CrmStageRepository stageRepository;
  private final CrmTaskRepository taskRepository;
  private final CrmCampaignRepository campaignRepository;
  private final CrmRecordScopeService recordScopeService;
  private final ScoreBandService scoreBandService;

  public AnalyticsService(
      CrmLeadRepository leadRepository,
      CrmOpportunityRepository opportunityRepository,
      CrmStageRepository stageRepository,
      CrmTaskRepository taskRepository,
      CrmCampaignRepository campaignRepository,
      CrmRecordScopeService recordScopeService,
      ScoreBandService scoreBandService) {
    this.leadRepository = leadRepository;
    this.opportunityRepository = opportunityRepository;
    this.stageRepository = stageRepository;
    this.taskRepository = taskRepository;
    this.campaignRepository = campaignRepository;
    this.recordScopeService = recordScopeService;
    this.scoreBandService = scoreBandService;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> summary() {
    return buildSummary(
        CrmAccessScope.ORG, new ScopeListFilter(CrmAccessScope.ORG, null, null, List.of()));
  }

  /** Sprint 9 — role/scope dashboard pack (summary + optional pipeline + KPIs). */
  @Transactional(readOnly = true)
  public Map<String, Object> dashboard() {
    ScopeListFilter filter = recordScopeService.listFilter(null);
    CrmAccessScope scope = filter.scope();
    Map<String, Object> summaryData = buildSummary(scope, filter);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("scope", scope.name());
    out.put("authRole", TenantContextFilter.getCurrentAuthRole());
    out.put("rolePack", rolePack(scope));
    out.put("summary", summaryData);
    if (scope != CrmAccessScope.OWN) {
      out.put("pipeline", buildPipeline(scope, filter));
    }
    out.put("kpis", kpisFromSummary(summaryData));
    return out;
  }

  /** Sprint 9 — CSV export of current-scope dashboard KPIs + funnels. */
  @Transactional(readOnly = true)
  public String exportCsv() {
    Map<String, Object> dash = dashboard();
    @SuppressWarnings("unchecked")
    Map<String, Object> summaryData =
        dash.get("summary") instanceof Map<?, ?> m
            ? (Map<String, Object>) m
            : Map.of();
    StringBuilder sb = new StringBuilder();
    sb.append("section,key,value\n");
    sb.append("meta,scope,").append(csv(dash.get("scope"))).append('\n');
    sb.append("meta,authRole,").append(csv(dash.get("authRole"))).append('\n');
    @SuppressWarnings("unchecked")
    Map<String, Object> kpis =
        dash.get("kpis") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    for (Map.Entry<String, Object> e : kpis.entrySet()) {
      sb.append("kpi,").append(csv(e.getKey())).append(',').append(csv(e.getValue())).append('\n');
    }
    appendRows(sb, "leadFunnel", "stageName", summaryData.get("leadFunnel"));
    appendRows(sb, "leadSources", "sourceCode", summaryData.get("leadSources"));
    appendRows(sb, "campaigns", "campaignName", summaryData.get("campaigns"));
    appendRows(sb, "dealFunnel", "stageName", summaryData.get("dealFunnel"));
    return sb.toString();
  }

  @Transactional(readOnly = true)
  public Map<String, Object> pipeline() {
    return buildPipeline(
        CrmAccessScope.ORG, new ScopeListFilter(CrmAccessScope.ORG, null, null, List.of()));
  }

  /** Slice of summary/pipeline for scheduled report types. */
  @Transactional(readOnly = true)
  public Map<String, Object> reportSlice(String reportType) {
    Map<String, Object> full = summary();
    return switch (reportType == null ? "" : reportType.toUpperCase()) {
      case "SOURCES" -> Map.of("leadSources", full.get("leadSources"), "utmSources", full.get("utmSources"));
      case "CAMPAIGNS" -> Map.of("campaigns", full.get("campaigns"));
      case "OVERDUE" -> Map.of("overdueTasks", full.get("overdueTasks"), "openLeads", full.get("openLeads"));
      case "FUNNEL" ->
          Map.of(
              "leadFunnel", full.get("leadFunnel"),
              "dealFunnel", full.get("dealFunnel"),
              "openDeals", full.get("openDeals"),
              "wonDeals", full.get("wonDeals"),
              "lostDeals", full.get("lostDeals"));
      default -> full;
    };
  }

  private Map<String, Object> buildSummary(CrmAccessScope scope, ScopeListFilter filter) {
    String tenantId = TenantIds.require();
    Map<Long, String> stageNames =
        stageRepository.findByTenantIdAndDeletedAtIsNullOrderBySortOrderAsc(tenantId).stream()
            .collect(Collectors.toMap(CrmStageEntity::getId, CrmStageEntity::getName, (a, b) -> a));

    List<CrmLeadEntity> leads =
        leadRepository.findByTenantIdAndDeletedAtIsNull(tenantId).stream()
            .filter(l -> visible(l.getOwnerUserId(), l.getTeamId(), filter))
            .toList();

    Map<Long, Long> stageCounts = new LinkedHashMap<>();
    Map<String, Long> sourceCounts = new LinkedHashMap<>();
    Map<Long, Long> campaignCounts = new LinkedHashMap<>();
    Map<String, Long> utmCounts = new LinkedHashMap<>();
    for (CrmLeadEntity lead : leads) {
      if (lead.getStageId() != null) {
        stageCounts.merge(lead.getStageId(), 1L, Long::sum);
      }
      String src =
          lead.getSourceCode() == null || lead.getSourceCode().isBlank()
              ? "(none)"
              : lead.getSourceCode();
      sourceCounts.merge(src, 1L, Long::sum);
      if (lead.getCampaignId() != null) {
        campaignCounts.merge(lead.getCampaignId(), 1L, Long::sum);
      }
      String utm =
          lead.getUtmSource() == null || lead.getUtmSource().isBlank()
              ? "(none)"
              : lead.getUtmSource();
      utmCounts.merge(utm, 1L, Long::sum);
    }

    List<Map<String, Object>> leadFunnel = new ArrayList<>();
    for (Map.Entry<Long, Long> e : stageCounts.entrySet()) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("stageId", e.getKey());
      m.put("stageName", stageNames.getOrDefault(e.getKey(), String.valueOf(e.getKey())));
      m.put("count", e.getValue());
      leadFunnel.add(m);
    }

    List<Map<String, Object>> sources = new ArrayList<>();
    for (Map.Entry<String, Long> e : sourceCounts.entrySet()) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("sourceCode", e.getKey());
      m.put("count", e.getValue());
      sources.add(m);
    }

    Map<Long, String> campaignNames =
        campaignRepository.findByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc(tenantId).stream()
            .collect(Collectors.toMap(CrmCampaignEntity::getId, CrmCampaignEntity::getName, (a, b) -> a));
    List<Map<String, Object>> campaigns = new ArrayList<>();
    for (Map.Entry<Long, Long> e : campaignCounts.entrySet()) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("campaignId", e.getKey());
      m.put("campaignName", campaignNames.getOrDefault(e.getKey(), String.valueOf(e.getKey())));
      m.put("count", e.getValue());
      campaigns.add(m);
    }

    List<Map<String, Object>> utmSources = new ArrayList<>();
    for (Map.Entry<String, Long> e : utmCounts.entrySet()) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("utmSource", e.getKey());
      m.put("count", e.getValue());
      utmSources.add(m);
    }

    List<CrmOpportunityEntity> openOpps =
        opportunityRepository.findByTenantIdAndStatusAndDeletedAtIsNull(tenantId, "OPEN").stream()
            .filter(o -> visible(o.getOwnerUserId(), o.getTeamId(), filter))
            .toList();
    List<CrmOpportunityEntity> wonOpps =
        opportunityRepository.findByTenantIdAndStatusAndDeletedAtIsNull(tenantId, "WON").stream()
            .filter(o -> visible(o.getOwnerUserId(), o.getTeamId(), filter))
            .toList();
    List<CrmOpportunityEntity> lostOpps =
        opportunityRepository.findByTenantIdAndStatusAndDeletedAtIsNull(tenantId, "LOST").stream()
            .filter(o -> visible(o.getOwnerUserId(), o.getTeamId(), filter))
            .toList();

    Map<Long, long[]> dealStage = new LinkedHashMap<>();
    Map<Long, BigDecimal> dealAmount = new LinkedHashMap<>();
    for (CrmOpportunityEntity o : openOpps) {
      Long stageId = o.getStageId();
      dealStage.computeIfAbsent(stageId, k -> new long[1])[0]++;
      dealAmount.merge(
          stageId, o.getAmount() == null ? BigDecimal.ZERO : o.getAmount(), BigDecimal::add);
    }
    List<Map<String, Object>> dealFunnel = new ArrayList<>();
    for (Map.Entry<Long, long[]> e : dealStage.entrySet()) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("stageId", e.getKey());
      m.put("stageName", stageNames.getOrDefault(e.getKey(), String.valueOf(e.getKey())));
      m.put("count", e.getValue()[0]);
      m.put("amount", dealAmount.getOrDefault(e.getKey(), BigDecimal.ZERO));
      dealFunnel.add(m);
    }

    Instant now = Instant.now();
    long overdueTasks =
        taskRepository
            .findByTenantIdAndStatusAndDueAtBeforeAndDeletedAtIsNullOrderByDueAtAsc(
                tenantId, "OPEN", now)
            .stream()
            .filter(t -> visibleTask(t, filter))
            .count();

    int hotMin = scoreBandService.hotMin();
    long hotLeads =
        leads.stream()
            .filter(l -> "OPEN".equalsIgnoreCase(l.getStatus()) && l.getScore() >= hotMin)
            .count();
    long openLeads =
        leads.stream().filter(l -> "OPEN".equalsIgnoreCase(l.getStatus())).count();

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("leadFunnel", leadFunnel);
    out.put("leadSources", sources);
    out.put("campaigns", campaigns);
    out.put("utmSources", utmSources);
    out.put("dealFunnel", dealFunnel);
    out.put("openDeals", openOpps.size());
    out.put("wonDeals", wonOpps.size());
    out.put("lostDeals", lostOpps.size());
    out.put("overdueTasks", overdueTasks);
    out.put("openLeads", openLeads);
    out.put("hotLeads", hotLeads);
    out.put("hotMinScore", hotMin);
    out.put("scope", scope.name());
    return out;
  }

  private Map<String, Object> buildPipeline(CrmAccessScope scope, ScopeListFilter filter) {
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

    Map<Long, long[]> stageDwell = new LinkedHashMap<>();
    List<Map<String, Object>> agingRows = new ArrayList<>();

    List<CrmOpportunityEntity> open =
        opportunityRepository.findByTenantIdAndStatusAndDeletedAtIsNull(tenantId, "OPEN").stream()
            .filter(o -> visible(o.getOwnerUserId(), o.getTeamId(), filter))
            .toList();

    for (CrmOpportunityEntity o : open) {
      Instant anchor = o.getUpdatedAt() != null ? o.getUpdatedAt() : o.getCreatedAt();
      long days = anchor == null ? 0 : Duration.between(anchor, now).toDays();
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
      m.put(
          "label",
          b.equals("0_7")
              ? "0–7 days"
              : b.equals("8_14") ? "8–14 days" : b.equals("15_30") ? "15–30 days" : "31+ days");
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
      m.put(
          "avgDaysInStage",
          e.getValue()[0] == 0
              ? 0
              : Math.round((double) e.getValue()[1] / e.getValue()[0] * 10) / 10.0);
      velocityByStage.add(m);
    }

    List<CrmOpportunityEntity> won =
        opportunityRepository.findByTenantIdAndStatusAndDeletedAtIsNull(tenantId, "WON").stream()
            .filter(o -> visible(o.getOwnerUserId(), o.getTeamId(), filter))
            .toList();
    List<CrmOpportunityEntity> lost =
        opportunityRepository.findByTenantIdAndStatusAndDeletedAtIsNull(tenantId, "LOST").stream()
            .filter(o -> visible(o.getOwnerUserId(), o.getTeamId(), filter))
            .toList();

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("agingBuckets", agingBuckets);
    out.put("agingDeals", agingRows);
    out.put("velocityByStage", velocityByStage);
    out.put("avgDaysToWon", avgCycleDays(won, now));
    out.put("avgDaysToLost", avgCycleDays(lost, now));
    out.put("wonByReason", reasonBreakdown(won));
    out.put("lostByReason", reasonBreakdown(lost));
    out.put("scope", scope.name());
    out.put(
        "counts",
        Map.of(
            "open", open.size(),
            "won", won.size(),
            "lost", lost.size()));
    return out;
  }

  private static List<String> rolePack(CrmAccessScope scope) {
    return switch (scope) {
      case OWN -> List.of("overdueTasks", "openLeads", "hotLeads", "openDeals");
      case TEAM ->
          List.of(
              "overdueTasks",
              "openDeals",
              "pipelineAmount",
              "dealFunnel",
              "agingBuckets",
              "velocityByStage");
      case ORG ->
          List.of(
              "openDeals",
              "wonDeals",
              "pipelineAmount",
              "overdueTasks",
              "lostDeals",
              "leadFunnel",
              "leadSources",
              "campaigns",
              "agingBuckets");
    };
  }

  private static Map<String, Object> kpisFromSummary(Map<String, Object> summaryData) {
    Map<String, Object> kpis = new LinkedHashMap<>();
    kpis.put("openDeals", summaryData.getOrDefault("openDeals", 0));
    kpis.put("wonDeals", summaryData.getOrDefault("wonDeals", 0));
    kpis.put("lostDeals", summaryData.getOrDefault("lostDeals", 0));
    kpis.put("overdueTasks", summaryData.getOrDefault("overdueTasks", 0));
    kpis.put("openLeads", summaryData.getOrDefault("openLeads", 0));
    kpis.put("hotLeads", summaryData.getOrDefault("hotLeads", 0));
    BigDecimal pipelineAmount = BigDecimal.ZERO;
    Object funnel = summaryData.get("dealFunnel");
    if (funnel instanceof List<?> list) {
      for (Object row : list) {
        if (row instanceof Map<?, ?> m && m.get("amount") != null) {
          Object amt = m.get("amount");
          if (amt instanceof BigDecimal bd) {
            pipelineAmount = pipelineAmount.add(bd);
          } else {
            pipelineAmount = pipelineAmount.add(BigDecimal.valueOf(((Number) amt).doubleValue()));
          }
        }
      }
    }
    kpis.put("pipelineAmount", pipelineAmount);
    return kpis;
  }

  private static boolean visible(String ownerUserId, String teamId, ScopeListFilter filter) {
    if (filter.scope() == CrmAccessScope.ORG) {
      return true;
    }
    String user = filter.scopeUserId();
    if (user != null && Objects.equals(user, ownerUserId)) {
      return true;
    }
    if (filter.scope() == CrmAccessScope.TEAM
        && teamId != null
        && !teamId.isBlank()
        && filter.scopeTeamIds() != null
        && filter.scopeTeamIds().contains(teamId)) {
      return true;
    }
    return false;
  }

  private static boolean visibleTask(CrmTaskEntity t, ScopeListFilter filter) {
    if (filter.scope() == CrmAccessScope.ORG || filter.scope() == CrmAccessScope.TEAM) {
      return true;
    }
    return Objects.equals(filter.scopeUserId(), t.getOwnerUserId());
  }

  private static double avgCycleDays(List<CrmOpportunityEntity> closed, Instant now) {
    if (closed.isEmpty()) {
      return 0;
    }
    long total = 0;
    int n = 0;
    for (CrmOpportunityEntity o : closed) {
      Instant start = o.getCreatedAt();
      Instant end = o.getUpdatedAt() != null ? o.getUpdatedAt() : now;
      if (start == null) {
        continue;
      }
      total += Duration.between(start, end).toDays();
      n++;
    }
    return n == 0 ? 0 : Math.round((double) total / n * 10) / 10.0;
  }

  private static List<Map<String, Object>> reasonBreakdown(List<CrmOpportunityEntity> closed) {
    Map<String, long[]> counts = new LinkedHashMap<>();
    Map<String, BigDecimal> amounts = new LinkedHashMap<>();
    for (CrmOpportunityEntity o : closed) {
      String reason =
          o.getCloseReasonCode() == null || o.getCloseReasonCode().isBlank()
              ? "(none)"
              : o.getCloseReasonCode();
      counts.computeIfAbsent(reason, k -> new long[1])[0]++;
      amounts.merge(reason, o.getAmount() == null ? BigDecimal.ZERO : o.getAmount(), BigDecimal::add);
    }
    List<Map<String, Object>> rows = new ArrayList<>();
    for (Map.Entry<String, long[]> e : counts.entrySet()) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("reasonCode", e.getKey());
      m.put("count", e.getValue()[0]);
      m.put("amount", amounts.getOrDefault(e.getKey(), BigDecimal.ZERO));
      rows.add(m);
    }
    return rows;
  }

  private static void appendRows(StringBuilder sb, String section, String keyField, Object rows) {
    if (!(rows instanceof List<?> list)) {
      return;
    }
    for (Object row : list) {
      if (!(row instanceof Map<?, ?> m)) {
        continue;
      }
      sb.append(section)
          .append(',')
          .append(csv(m.get(keyField)))
          .append(',')
          .append(csv(m.get("count") != null ? m.get("count") : m.get("amount")))
          .append('\n');
    }
  }

  private static String csv(Object value) {
    if (value == null) {
      return "";
    }
    String s = String.valueOf(value);
    if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
      return '"' + s.replace("\"", "\"\"") + '"';
    }
    return s;
  }
}
