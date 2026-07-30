package com.shopmanagement.crmservice.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.shopmanagement.crmservice.api.CrmLeadApi.TeamMemberRequest;
import com.shopmanagement.crmservice.api.CrmLeadApi.TeamMemberResponse;
import com.shopmanagement.crmservice.entitlement.CrmEntitlementGuard;
import com.shopmanagement.crmservice.service.AssignmentService;

@RestController
@RequestMapping("/api/v1/crm/assignment")
public class AssignmentController {

  private final AssignmentService assignmentService;
  private final CrmEntitlementGuard entitlementGuard;

  public AssignmentController(AssignmentService assignmentService, CrmEntitlementGuard entitlementGuard) {
    this.assignmentService = assignmentService;
    this.entitlementGuard = entitlementGuard;
  }

  @PostMapping("/members")
  public TeamMemberResponse upsert(@RequestBody TeamMemberRequest body) {
    entitlementGuard.requireCrmAccess();
    return assignmentService.upsertMember(body);
  }

  @GetMapping("/members")
  public List<TeamMemberResponse> list(@RequestParam(required = false) String teamId) {
    entitlementGuard.requireCrmAccess();
    return assignmentService.listMembers(teamId);
  }

  @DeleteMapping("/members/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deactivate(@org.springframework.web.bind.annotation.PathVariable Long id) {
    entitlementGuard.requireCrmAccess();
    assignmentService.deactivateMember(id);
  }
}
