package com.shopmanagement.crmservice.jobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.shopmanagement.crmservice.filter.TenantContextFilter;
import com.shopmanagement.crmservice.persistence.repo.CrmWorkspaceRepository;
import com.shopmanagement.crmservice.service.CaptureJobService;
import com.shopmanagement.crmservice.service.LeadCaptureService;
import com.shopmanagement.crmservice.service.ReportAclService;
import com.shopmanagement.crmservice.service.SequenceService;
import com.shopmanagement.crmservice.service.SlaTaskService;

/** Runs sequence steps, SLA aging, and due report snapshots. These used to wait for a manual POST. */
@Component
@ConditionalOnProperty(prefix = "crm.jobs", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CrmMaintenanceJobs {

  private static final Logger log = LoggerFactory.getLogger(CrmMaintenanceJobs.class);

  private final CrmWorkspaceRepository workspaceRepository;
  private final SequenceService sequenceService;
  private final SlaTaskService slaTaskService;
  private final ReportAclService reportAclService;
  private final LeadCaptureService leadCaptureService;
  private final CaptureJobService captureJobService;

  public CrmMaintenanceJobs(
      CrmWorkspaceRepository workspaceRepository,
      SequenceService sequenceService,
      SlaTaskService slaTaskService,
      ReportAclService reportAclService,
      LeadCaptureService leadCaptureService,
      CaptureJobService captureJobService) {
    this.workspaceRepository = workspaceRepository;
    this.sequenceService = sequenceService;
    this.slaTaskService = slaTaskService;
    this.reportAclService = reportAclService;
    this.leadCaptureService = leadCaptureService;
    this.captureJobService = captureJobService;
  }

  @Scheduled(fixedDelayString = "${crm.jobs.fixed-delay-ms:60000}")
  public void runDueWork() {
    try {
      captureJobService.processDue();
    } catch (RuntimeException ex) {
      log.warn("CRM capture job sweep failed: {}", ex.getMessage());
    }
    for (String tenantId : workspaceRepository.findActiveTenantIds()) {
      if (tenantId == null || tenantId.isBlank()) {
        continue;
      }
      TenantContextFilter.bindTenant(tenantId);
      try {
        sequenceService.processDue(50);
        slaTaskService.processAging();
        reportAclService.runDueReports();
        leadCaptureService.backfillIfEnabled(tenantId);
      } catch (RuntimeException ex) {
        log.warn("CRM maintenance failed for tenant {}: {}", tenantId, ex.getMessage());
      } finally {
        TenantContextFilter.clearTenantForTests();
      }
    }
  }
}
