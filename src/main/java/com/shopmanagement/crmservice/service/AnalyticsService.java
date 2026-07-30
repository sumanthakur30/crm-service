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
}
