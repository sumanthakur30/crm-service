package com.shopmanagement.crmservice.web;

import java.util.Map;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shopmanagement.crmservice.service.CaseService;

/**
 * Public CSAT survey — no X-Tenant-Id; tenant resolved from case csat_public_token.
 * Allowed by {@code TenantContextFilter} under {@code /api/v1/crm/public/}.
 */
@RestController
@RequestMapping("/api/v1/crm/public/cases")
public class PublicCaseController {

  private final CaseService caseService;

  public PublicCaseController(CaseService caseService) {
    this.caseService = caseService;
  }

  @PostMapping("/{token}/csat")
  public Map<String, Object> submitCsat(
      @PathVariable String token, @RequestBody Map<String, Object> body) {
    return caseService.submitCsatByPublicToken(token, body);
  }
}
