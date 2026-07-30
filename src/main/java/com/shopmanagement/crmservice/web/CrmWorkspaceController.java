package com.shopmanagement.crmservice.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shopmanagement.crmservice.api.CrmLeadApi.PipelineResponse;
import com.shopmanagement.crmservice.api.CrmLeadApi.StageResponse;
import com.shopmanagement.crmservice.api.CrmLeadApi.StatusResponse;
import com.shopmanagement.crmservice.api.CrmLeadApi.WorkspaceBootstrapRequest;
import com.shopmanagement.crmservice.api.CrmLeadApi.WorkspaceResponse;
import com.shopmanagement.crmservice.config.CrmProperties;
import com.shopmanagement.crmservice.entitlement.CrmEntitlementGuard;
import com.shopmanagement.crmservice.service.WorkspaceBootstrapService;

@RestController
@RequestMapping("/api/v1/crm")
public class CrmWorkspaceController {

  private final WorkspaceBootstrapService workspaceBootstrapService;
  private final CrmEntitlementGuard entitlementGuard;
  private final CrmProperties crmProperties;

  public CrmWorkspaceController(
      WorkspaceBootstrapService workspaceBootstrapService,
      CrmEntitlementGuard entitlementGuard,
      CrmProperties crmProperties) {
    this.workspaceBootstrapService = workspaceBootstrapService;
    this.entitlementGuard = entitlementGuard;
    this.crmProperties = crmProperties;
  }

  @GetMapping("/status")
  public StatusResponse status() {
    return new StatusResponse("crm-service", "3", crmProperties.isEnabled());
  }

  @PostMapping("/workspaces/bootstrap")
  public WorkspaceResponse bootstrap(@RequestBody(required = false) WorkspaceBootstrapRequest body) {
    entitlementGuard.requireCrmAccess();
    return workspaceBootstrapService.bootstrap(
        body == null ? new WorkspaceBootstrapRequest(null, null) : body);
  }

  @GetMapping("/workspaces/current")
  public WorkspaceResponse current() {
    entitlementGuard.requireCrmAccess();
    return workspaceBootstrapService.current();
  }

  @GetMapping("/pipelines")
  public List<PipelineResponse> pipelines() {
    entitlementGuard.requireCrmAccess();
    return workspaceBootstrapService.listPipelines();
  }

  @GetMapping("/pipelines/{pipelineId}/stages")
  public List<StageResponse> stages(@PathVariable Long pipelineId) {
    entitlementGuard.requireCrmAccess();
    return workspaceBootstrapService.listStages(pipelineId);
  }

  @GetMapping("/templates")
  public List<String> templates() {
    return workspaceBootstrapService.listTemplates();
  }
}
