package com.shopmanagement.crmservice.service;

import java.time.Year;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.shopmanagement.crmservice.filter.TenantContextFilter;
import com.shopmanagement.crmservice.persistence.entity.CrmAssignmentRuleEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmContactEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmImportBatchEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmLeadEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmWorkspaceEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmAssignmentRuleRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmContactRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmImportBatchRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmLeadRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmWorkspaceRepository;

/** Turns on only for the admission-consultancy template. Other templates are unchanged. */
@Service
public class ConsultancyPackService {

  public static final String TEMPLATE = "ADMISSION_CONSULTANCY";

  private final CrmWorkspaceRepository workspaceRepository;
  private final CrmLeadRepository leadRepository;
  private final CrmContactRepository contactRepository;
  private final CrmImportBatchRepository importBatchRepository;
  private final CrmAssignmentRuleRepository ruleRepository;

  public ConsultancyPackService(
      CrmWorkspaceRepository workspaceRepository,
      CrmLeadRepository leadRepository,
      CrmContactRepository contactRepository,
      CrmImportBatchRepository importBatchRepository,
      CrmAssignmentRuleRepository ruleRepository) {
    this.workspaceRepository = workspaceRepository;
    this.leadRepository = leadRepository;
    this.contactRepository = contactRepository;
    this.importBatchRepository = importBatchRepository;
    this.ruleRepository = ruleRepository;
  }

  @Transactional
  public void ensure(String tenantId) {
    CrmWorkspaceEntity workspace = workspace(tenantId).orElse(null);
    if (workspace == null || !TEMPLATE.equalsIgnoreCase(workspace.getTemplateCode())) {
      return;
    }
    if (workspace.getSettingsJson() == null) {
      workspace.setSettingsJson(new java.util.LinkedHashMap<>());
    }
    if (workspace.getSettingsJson().get("consultancyRepeat") == null) {
      workspace.getSettingsJson().put("consultancyRepeat", "NEW");
      workspace.getSettingsJson().put("consultancyRepeatOwner", "KEEP");
      workspace.touch();
      workspaceRepository.save(workspace);
    }
  }

  public boolean newEnquiryOnRepeat(String tenantId) {
    return workspace(tenantId)
        .filter(row -> TEMPLATE.equalsIgnoreCase(row.getTemplateCode()))
        .map(row -> row.getSettingsJson() != null && !"KEEP".equalsIgnoreCase(text(row.getSettingsJson().get("consultancyRepeat"))))
        .orElse(false);
  }

  public boolean keepPreviousOwner(String tenantId) {
    return workspace(tenantId)
        .map(row -> row.getSettingsJson() == null || !"REASSIGN".equalsIgnoreCase(text(row.getSettingsJson().get("consultancyRepeatOwner"))))
        .orElse(true);
  }

  @Transactional
  public void stampEnquiry(CrmLeadEntity lead) {
    if (lead == null || lead.getId() == null || !enabled(lead.getTenantId())) {
      return;
    }
    if (lead.getEnquiryNo() == null || lead.getEnquiryNo().isBlank()) {
      lead.setEnquiryNo(code("ENQ", lead.getId()));
      leadRepository.save(lead);
    }
  }

  @Transactional
  public void stampStudent(String tenantId, Long contactId) {
    if (contactId == null || !enabled(tenantId)) {
      return;
    }
    CrmContactEntity contact = contactRepository.findById(contactId).orElse(null);
    if (contact == null) {
      return;
    }
    if (contact.getStudentCode() != null && !contact.getStudentCode().isBlank()) {
      return;
    }
    contact.setStudentCode(code("STU", contact.getId()));
    if (contact.getContactKind() == null || "OTHER".equalsIgnoreCase(contact.getContactKind())) {
      contact.setContactKind("STUDENT");
    }
    contact.touch();
    contactRepository.save(contact);
  }

  public Optional<String> ruleOwner(String tenantId, String sourceCode, String courseCode, String city) {
    if (!enabled(tenantId)) {
      return Optional.empty();
    }
    List<CrmAssignmentRuleEntity> rules = ruleRepository.findByTenantIdAndActiveTrueOrderBySortOrderAscIdAsc(tenantId);
    for (CrmAssignmentRuleEntity rule : rules) {
      if (matches(rule.getSourceCode(), sourceCode)
          && matches(rule.getCourseCode(), courseCode)
          && matches(rule.getCity(), city)) {
        return Optional.of(rule.getOwnerUserId());
      }
    }
    return Optional.empty();
  }

  @Transactional
  public void recordImport(String tenantId, String label, int rows, int created, int skipped) {
    if (!enabled(tenantId)) {
      return;
    }
    CrmImportBatchEntity batch = new CrmImportBatchEntity();
    batch.setTenantId(tenantId);
    batch.setLabel(label == null || label.isBlank() ? "import" : label.trim());
    batch.setUploadedBy(TenantContextFilter.getCurrentUserId());
    batch.setRowCount(rows);
    batch.setAcceptedCount(created);
    batch.setRejectedCount(skipped);
    importBatchRepository.save(batch);
  }

  public void requireEnabled(String tenantId) {
    if (!enabled(tenantId)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Choose the Admission consultancy template to use this desk");
    }
  }

  public void denyCounsellorMoney() {
    if (moneyHidden()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This role cannot open the fee or commission ledgers");
    }
  }

  public boolean moneyHidden() {
    String role = TenantContextFilter.getCurrentAuthRole();
    if (role == null || role.isBlank()) {
      return false;
    }
    String normalized = role.trim().toUpperCase(Locale.ROOT);
    return normalized.contains("COUNSELLOR") || normalized.contains("TELECALLER");
  }

  public boolean enabled(String tenantId) {
    return workspace(tenantId).map(row -> TEMPLATE.equalsIgnoreCase(row.getTemplateCode())).orElse(false);
  }

  private Optional<CrmWorkspaceEntity> workspace(String tenantId) {
    if (tenantId == null || tenantId.isBlank()) {
      return Optional.empty();
    }
    return workspaceRepository.findByTenantIdAndDeletedAtIsNull(tenantId);
  }

  private static boolean matches(String ruleValue, String actual) {
    if (ruleValue == null || ruleValue.isBlank()) {
      return true;
    }
    return actual != null && ruleValue.trim().equalsIgnoreCase(actual.trim());
  }

  private static String text(Object value) {
    return value == null ? "" : String.valueOf(value).trim();
  }

  static String code(String prefix, Long id) {
    int year = Year.now(ZoneId.of("Asia/Kolkata")).getValue();
    return prefix + "-" + year + "-" + String.format("%06d", id);
  }
}
