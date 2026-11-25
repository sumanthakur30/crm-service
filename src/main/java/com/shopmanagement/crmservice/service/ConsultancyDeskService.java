package com.shopmanagement.crmservice.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.shopmanagement.crmservice.persistence.entity.CrmAdmissionSessionEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmAssignmentRuleEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmCommercialAgreementEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmCommercialTxnEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmConsultantEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmCourseEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmCourseOfferEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmInstitutionEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmLeadEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmPlacementEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmAdmissionSessionRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmAssignmentRuleRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmCommercialAgreementRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmCommercialTxnRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmConsultantRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmCourseOfferRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmCourseRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmImportBatchRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmInstitutionRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmLeadRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmPlacementRepository;
import com.shopmanagement.crmservice.service.ConsultancyMoney.Line;
import com.shopmanagement.crmservice.service.ConsultancyMoney.Summary;
import com.shopmanagement.crmservice.support.TenantIds;

/** Partner catalogue, applications, and the three ledgers. Rates come from the tenant, not from code. */
@Service
public class ConsultancyDeskService {

  public record Named(Long id, String code, String name, boolean active) {}

  public record ConsultantView(Long id, String code, String name, String phone, String city, boolean active) {}

  public record OfferView(Long id, Long institutionId, Long courseId, Long sessionId, BigDecimal feeAmount) {}

  public record PlacementView(
      Long id,
      String applicationNo,
      Long leadId,
      Long institutionId,
      Long courseId,
      Long sessionId,
      Long consultantId,
      String status,
      String enrolmentNo,
      String cancelReason) {}

  public record AgreementView(
      Long id, String party, String method, BigDecimal rate, String baseKind, String triggerCode, Long institutionId, Long consultantId) {}

  public record Desk(
      boolean enabled,
      List<Named> institutions,
      List<Named> courses,
      List<Named> sessions,
      List<ConsultantView> consultants,
      List<OfferView> offers,
      List<PlacementView> placements,
      List<AgreementView> agreements,
      Summary contribution,
      int importBatches) {}

  public record CodeName(String code, String name, String category) {}

  public record ConsultantBody(String code, String name, String phone, String city) {}

  public record OfferBody(Long institutionId, Long courseId, Long sessionId, BigDecimal feeAmount) {}

  public record PlacementBody(Long leadId, Long institutionId, Long courseId, Long sessionId, Long consultantId) {}

  public record ConfirmBody(String enrolmentNo) {}

  public record CancelBody(String reason) {}

  public record TxnBody(
      String ledger, String txnKind, BigDecimal amount, String counterparty, String referenceNo, LocalDate txnDate, String note) {}

  public record AgreementBody(
      String party,
      String method,
      BigDecimal rate,
      String baseKind,
      String triggerCode,
      Long institutionId,
      Long courseId,
      Long sessionId,
      Long consultantId,
      LocalDate effectiveFrom) {}

  public record RuleBody(String sourceCode, String courseCode, String city, String ownerUserId, Integer sortOrder) {}

  private final ConsultancyPackService pack;
  private final CrmInstitutionRepository institutionRepository;
  private final CrmCourseRepository courseRepository;
  private final CrmAdmissionSessionRepository sessionRepository;
  private final CrmConsultantRepository consultantRepository;
  private final CrmCourseOfferRepository offerRepository;
  private final CrmPlacementRepository placementRepository;
  private final CrmCommercialAgreementRepository agreementRepository;
  private final CrmCommercialTxnRepository txnRepository;
  private final CrmLeadRepository leadRepository;
  private final CrmImportBatchRepository importBatchRepository;
  private final CrmAssignmentRuleRepository ruleRepository;

  public ConsultancyDeskService(
      ConsultancyPackService pack,
      CrmInstitutionRepository institutionRepository,
      CrmCourseRepository courseRepository,
      CrmAdmissionSessionRepository sessionRepository,
      CrmConsultantRepository consultantRepository,
      CrmCourseOfferRepository offerRepository,
      CrmPlacementRepository placementRepository,
      CrmCommercialAgreementRepository agreementRepository,
      CrmCommercialTxnRepository txnRepository,
      CrmLeadRepository leadRepository,
      CrmImportBatchRepository importBatchRepository,
      CrmAssignmentRuleRepository ruleRepository) {
    this.pack = pack;
    this.institutionRepository = institutionRepository;
    this.courseRepository = courseRepository;
    this.sessionRepository = sessionRepository;
    this.consultantRepository = consultantRepository;
    this.offerRepository = offerRepository;
    this.placementRepository = placementRepository;
    this.agreementRepository = agreementRepository;
    this.txnRepository = txnRepository;
    this.leadRepository = leadRepository;
    this.importBatchRepository = importBatchRepository;
    this.ruleRepository = ruleRepository;
  }

  @Transactional(readOnly = true)
  public Desk desk() {
    String tenantId = TenantIds.require();
    if (!pack.enabled(tenantId)) {
      return new Desk(false, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), ConsultancyMoney.summarize(List.of()), 0);
    }
    boolean hideMoney = pack.moneyHidden();
    return new Desk(
        true,
        institutionRepository.findByTenantIdAndDeletedAtIsNullOrderByNameAsc(tenantId).stream()
            .map(row -> new Named(row.getId(), row.getCode(), row.getName(), row.isActive()))
            .toList(),
        courseRepository.findByTenantIdAndDeletedAtIsNullOrderByNameAsc(tenantId).stream()
            .map(row -> new Named(row.getId(), row.getCode(), row.getName(), row.isActive()))
            .toList(),
        sessionRepository.findByTenantIdAndDeletedAtIsNullOrderByNameAsc(tenantId).stream()
            .map(row -> new Named(row.getId(), row.getCode(), row.getName(), row.isActive()))
            .toList(),
        consultantRepository.findByTenantIdAndDeletedAtIsNullOrderByNameAsc(tenantId).stream()
            .map(row -> new ConsultantView(row.getId(), row.getCode(), row.getName(), row.getPhone(), row.getCity(), row.isActive()))
            .toList(),
        offerRepository.findByTenantIdAndDeletedAtIsNullOrderByIdAsc(tenantId).stream()
            .map(row -> new OfferView(row.getId(), row.getInstitutionId(), row.getCourseId(), row.getSessionId(), row.getFeeAmount()))
            .toList(),
        placementRepository.findByTenantIdAndDeletedAtIsNullOrderByIdDesc(tenantId).stream().map(this::view).toList(),
        hideMoney ? List.of() : agreementRepository.findByTenantIdAndDeletedAtIsNullOrderByIdDesc(tenantId).stream()
            .map(
                row ->
                    new AgreementView(
                        row.getId(),
                        row.getParty(),
                        row.getMethod(),
                        row.getRate(),
                        row.getBaseKind(),
                        row.getTriggerCode(),
                        row.getInstitutionId(),
                        row.getConsultantId()))
            .toList(),
        hideMoney ? ConsultancyMoney.summarize(List.of()) : contributionOf(tenantId, null),
        importBatchRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).size());
  }

  @Transactional
  public Named saveInstitution(CodeName body) {
    String tenantId = require();
    CrmInstitutionEntity row =
        institutionRepository
            .findByTenantIdAndCodeAndDeletedAtIsNull(tenantId, code(body))
            .orElseGet(CrmInstitutionEntity::new);
    if (row.getId() == null) {
      row.setTenantId(tenantId);
      row.setCode(code(body));
    }
    row.setName(name(body));
    row.setActive(true);
    row.touch();
    row = institutionRepository.save(row);
    return new Named(row.getId(), row.getCode(), row.getName(), row.isActive());
  }

  @Transactional
  public Named saveCourse(CodeName body) {
    String tenantId = require();
    CrmCourseEntity row =
        courseRepository.findByTenantIdAndCodeAndDeletedAtIsNull(tenantId, code(body)).orElseGet(CrmCourseEntity::new);
    if (row.getId() == null) {
      row.setTenantId(tenantId);
      row.setCode(code(body));
    }
    row.setName(name(body));
    row.setCategory(body == null ? null : blank(body.category()));
    row.setActive(true);
    row.touch();
    row = courseRepository.save(row);
    return new Named(row.getId(), row.getCode(), row.getName(), row.isActive());
  }

  @Transactional
  public Named saveSession(CodeName body) {
    String tenantId = require();
    CrmAdmissionSessionEntity row =
        sessionRepository.findByTenantIdAndCodeAndDeletedAtIsNull(tenantId, code(body)).orElseGet(CrmAdmissionSessionEntity::new);
    if (row.getId() == null) {
      row.setTenantId(tenantId);
      row.setCode(code(body));
    }
    row.setName(name(body));
    row.setActive(true);
    row.touch();
    row = sessionRepository.save(row);
    return new Named(row.getId(), row.getCode(), row.getName(), row.isActive());
  }

  @Transactional
  public ConsultantView saveConsultant(ConsultantBody body) {
    String tenantId = require();
    if (body == null || body.code() == null || body.code().isBlank() || body.name() == null || body.name().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Consultant code and name are required");
    }
    String code = body.code().trim().toUpperCase(Locale.ROOT);
    CrmConsultantEntity row =
        consultantRepository.findByTenantIdAndCodeAndDeletedAtIsNull(tenantId, code).orElseGet(CrmConsultantEntity::new);
    if (row.getId() == null) {
      row.setTenantId(tenantId);
      row.setCode(code);
    }
    row.setName(body.name().trim());
    row.setPhone(blank(body.phone()));
    row.setCity(blank(body.city()));
    row.setActive(true);
    row.touch();
    row = consultantRepository.save(row);
    return new ConsultantView(row.getId(), row.getCode(), row.getName(), row.getPhone(), row.getCity(), row.isActive());
  }

  @Transactional
  public OfferView saveOffer(OfferBody body) {
    String tenantId = require();
    if (body == null || body.institutionId() == null || body.courseId() == null || body.sessionId() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Institution, course, and session are required");
    }
    CrmCourseOfferEntity row = new CrmCourseOfferEntity();
    row.setTenantId(tenantId);
    row.setInstitutionId(body.institutionId());
    row.setCourseId(body.courseId());
    row.setSessionId(body.sessionId());
    row.setFeeAmount(body.feeAmount() == null ? BigDecimal.ZERO : body.feeAmount());
    row.setActive(true);
    try {
      row = offerRepository.saveAndFlush(row);
    } catch (DataIntegrityViolationException ex) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "That course is already offered for this session");
    }
    return new OfferView(row.getId(), row.getInstitutionId(), row.getCourseId(), row.getSessionId(), row.getFeeAmount());
  }

  @Transactional
  public PlacementView openPlacement(PlacementBody body) {
    String tenantId = require();
    if (body == null || body.leadId() == null || body.institutionId() == null || body.courseId() == null || body.sessionId() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lead, institution, course, and session are required");
    }
    CrmLeadEntity lead =
        leadRepository
            .findById(body.leadId())
            .filter(row -> tenantId.equals(row.getTenantId()) && row.getDeletedAt() == null)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead not found"));
    CrmPlacementEntity row = new CrmPlacementEntity();
    row.setTenantId(tenantId);
    row.setLeadId(lead.getId());
    row.setContactId(lead.getContactId());
    row.setInstitutionId(body.institutionId());
    row.setCourseId(body.courseId());
    row.setSessionId(body.sessionId());
    row.setConsultantId(body.consultantId());
    row.setApplicationNo("P" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 20));
    row.setStatus("SUBMITTED");
    row = placementRepository.save(row);
    row.setApplicationNo(ConsultancyPackService.code("APP", row.getId()));
    row.touch();
    row = placementRepository.save(row);
    seedFeeSchedule(row);
    return view(row);
  }

  @Transactional
  public PlacementView confirm(Long id, ConfirmBody body) {
    CrmPlacementEntity row = placement(id);
    row.setStatus("CONFIRMED");
    row.setConfirmedOn(LocalDate.now());
    row.setEnrolmentNo(body == null ? null : blank(body.enrolmentNo()));
    row.setCancelledOn(null);
    row.setCancelReason(null);
    row.touch();
    row = placementRepository.save(row);
    applyAgreements(row, "ADMISSION");
    return view(row);
  }

  @Transactional
  public PlacementView cancel(Long id, CancelBody body) {
    if (body == null || body.reason() == null || body.reason().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A cancellation reason is required");
    }
    CrmPlacementEntity row = placement(id);
    row.setStatus("CANCELLED");
    row.setCancelledOn(LocalDate.now());
    row.setCancelReason(body.reason().trim());
    row.touch();
    return view(placementRepository.save(row));
  }

  @Transactional
  public Summary addTxn(Long placementId, TxnBody body) {
    String tenantId = require();
    pack.denyCounsellorMoney();
    placement(placementId);
    if (body == null || body.amount() == null || body.amount().signum() < 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount is required");
    }
    String ledger = token(body.ledger(), "STUDENT_FEE", "INSTITUTION_COMMISSION", "CONSULTANT_PAYABLE", "EXPENSE");
    String kind = token(body.txnKind(), "SCHEDULE", "RECEIPT", "REFUND", "EXPECTED", "PAYMENT", "ADJUSTMENT");
    CrmCommercialTxnEntity txn = new CrmCommercialTxnEntity();
    txn.setTenantId(tenantId);
    txn.setPlacementId(placementId);
    txn.setLedger(ledger);
    txn.setTxnKind(kind);
    txn.setAmount(body.amount());
    txn.setCounterparty(blank(body.counterparty()));
    txn.setReferenceNo(blank(body.referenceNo()));
    txn.setTxnDate(body.txnDate() == null ? LocalDate.now() : body.txnDate());
    txn.setNote(blank(body.note()));
    try {
      txnRepository.saveAndFlush(txn);
    } catch (DataIntegrityViolationException ex) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "That reference is already used on this ledger");
    }
    if ("RECEIPT".equals(kind)) {
      CrmPlacementEntity placement = placement(placementId);
      if ("STUDENT_FEE".equals(ledger)) {
        applyAgreements(placement, "FEE_RECEIVED");
      } else if ("INSTITUTION_COMMISSION".equals(ledger)) {
        applyAgreements(placement, "COMMISSION_RECEIVED");
      }
    }
    return contributionOf(tenantId, placementId);
  }

  @Transactional
  public AgreementView saveAgreement(AgreementBody body) {
    String tenantId = require();
    pack.denyCounsellorMoney();
    if (body == null || body.rate() == null || body.effectiveFrom() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rate and effective date are required");
    }
    CrmCommercialAgreementEntity row = new CrmCommercialAgreementEntity();
    row.setTenantId(tenantId);
    row.setParty(token(body.party(), "INSTITUTION", "CONSULTANT"));
    row.setMethod(token(body.method(), "FIXED", "PERCENT"));
    row.setBaseKind(token(body.baseKind(), "FEE", "FIXED"));
    row.setTriggerCode(token(body.triggerCode(), "ADMISSION", "FEE_RECEIVED", "COMMISSION_RECEIVED"));
    row.setRate(body.rate());
    row.setInstitutionId(body.institutionId());
    row.setCourseId(body.courseId());
    row.setSessionId(body.sessionId());
    row.setConsultantId(body.consultantId());
    row.setEffectiveFrom(body.effectiveFrom());
    row.setActive(true);
    row = agreementRepository.save(row);
    return new AgreementView(
        row.getId(), row.getParty(), row.getMethod(), row.getRate(), row.getBaseKind(), row.getTriggerCode(), row.getInstitutionId(), row.getConsultantId());
  }

  @Transactional
  public void saveRule(RuleBody body) {
    String tenantId = require();
    if (body == null || body.ownerUserId() == null || body.ownerUserId().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An owner is required");
    }
    CrmAssignmentRuleEntity rule = new CrmAssignmentRuleEntity();
    rule.setTenantId(tenantId);
    rule.setSourceCode(blank(body.sourceCode()));
    rule.setCourseCode(blank(body.courseCode()));
    rule.setCity(blank(body.city()));
    rule.setOwnerUserId(body.ownerUserId().trim());
    rule.setSortOrder(body.sortOrder() == null ? 100 : body.sortOrder());
    rule.setActive(true);
    ruleRepository.save(rule);
  }

  @Transactional(readOnly = true)
  public Summary contribution(Long placementId) {
    String tenantId = require();
    pack.denyCounsellorMoney();
    return contributionOf(tenantId, placementId);
  }

  private Summary contributionOf(String tenantId, Long placementId) {
    List<CrmCommercialTxnEntity> rows =
        placementId == null
            ? txnRepository.findByTenantIdAndDeletedAtIsNull(tenantId)
            : txnRepository.findByTenantIdAndPlacementIdAndDeletedAtIsNull(tenantId, placementId);
    return ConsultancyMoney.summarize(
        rows.stream().map(row -> new Line(row.getLedger(), row.getTxnKind(), row.getAmount())).toList());
  }

  private CrmPlacementEntity placement(Long id) {
    String tenantId = require();
    return placementRepository
        .findByTenantIdAndIdAndDeletedAtIsNull(tenantId, id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
  }

  private PlacementView view(CrmPlacementEntity row) {
    return new PlacementView(
        row.getId(),
        row.getApplicationNo(),
        row.getLeadId(),
        row.getInstitutionId(),
        row.getCourseId(),
        row.getSessionId(),
        row.getConsultantId(),
        row.getStatus(),
        row.getEnrolmentNo(),
        row.getCancelReason());
  }

  private void seedFeeSchedule(CrmPlacementEntity placement) {
    BigDecimal fee = offerFee(placement);
    if (fee.signum() <= 0) {
      return;
    }
    CrmCommercialTxnEntity txn = new CrmCommercialTxnEntity();
    txn.setTenantId(placement.getTenantId());
    txn.setPlacementId(placement.getId());
    txn.setLedger("STUDENT_FEE");
    txn.setTxnKind("SCHEDULE");
    txn.setAmount(fee);
    txn.setTxnDate(LocalDate.now());
    txn.setNote("Fee schedule");
    txnRepository.save(txn);
  }

  private void applyAgreements(CrmPlacementEntity placement, String trigger) {
    BigDecimal fee = offerFee(placement);
    List<CrmCommercialTxnEntity> existing =
        txnRepository.findByTenantIdAndPlacementIdAndDeletedAtIsNull(placement.getTenantId(), placement.getId());
    for (CrmCommercialAgreementEntity agreement :
        agreementRepository.findByTenantIdAndDeletedAtIsNullOrderByIdDesc(placement.getTenantId())) {
      if (!agreement.isActive() || !trigger.equals(agreement.getTriggerCode())) {
        continue;
      }
      if (agreement.getEffectiveFrom() != null && agreement.getEffectiveFrom().isAfter(LocalDate.now())) {
        continue;
      }
      if (agreement.getEffectiveTo() != null && agreement.getEffectiveTo().isBefore(LocalDate.now())) {
        continue;
      }
      if (!covers(agreement.getInstitutionId(), placement.getInstitutionId())
          || !covers(agreement.getCourseId(), placement.getCourseId())
          || !covers(agreement.getSessionId(), placement.getSessionId())) {
        continue;
      }
      String ledger;
      if ("INSTITUTION".equals(agreement.getParty())) {
        ledger = "INSTITUTION_COMMISSION";
      } else if ("CONSULTANT".equals(agreement.getParty()) && placement.getConsultantId() != null
          && covers(agreement.getConsultantId(), placement.getConsultantId())) {
        ledger = "CONSULTANT_PAYABLE";
      } else {
        continue;
      }
      boolean posted =
          existing.stream().anyMatch(row -> ledger.equals(row.getLedger()) && "EXPECTED".equals(row.getTxnKind()));
      if (posted) {
        continue;
      }
      BigDecimal amount = amountOf(agreement, fee);
      if (amount.signum() <= 0) {
        continue;
      }
      CrmCommercialTxnEntity txn = new CrmCommercialTxnEntity();
      txn.setTenantId(placement.getTenantId());
      txn.setPlacementId(placement.getId());
      txn.setConsultantId("CONSULTANT_PAYABLE".equals(ledger) ? placement.getConsultantId() : null);
      txn.setLedger(ledger);
      txn.setTxnKind("EXPECTED");
      txn.setAmount(amount);
      txn.setTxnDate(LocalDate.now());
      txn.setNote("Agreement " + agreement.getId());
      txnRepository.save(txn);
    }
  }

  private BigDecimal offerFee(CrmPlacementEntity placement) {
    return offerRepository.findByTenantIdAndDeletedAtIsNullOrderByIdAsc(placement.getTenantId()).stream()
        .filter(
            row ->
                placement.getInstitutionId().equals(row.getInstitutionId())
                    && placement.getCourseId().equals(row.getCourseId())
                    && placement.getSessionId().equals(row.getSessionId())
                    && row.getFeeAmount() != null)
        .map(CrmCourseOfferEntity::getFeeAmount)
        .findFirst()
        .orElse(BigDecimal.ZERO);
  }

  private static boolean covers(Long ruleValue, Long actual) {
    return ruleValue == null || ruleValue.equals(actual);
  }

  private static BigDecimal amountOf(CrmCommercialAgreementEntity agreement, BigDecimal fee) {
    if ("PERCENT".equals(agreement.getMethod())) {
      return fee.multiply(agreement.getRate()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }
    return agreement.getRate().setScale(2, RoundingMode.HALF_UP);
  }

  private String require() {
    String tenantId = TenantIds.require();
    pack.requireEnabled(tenantId);
    return tenantId;
  }

  private static String code(CodeName body) {
    if (body == null || body.code() == null || body.code().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Code is required");
    }
    return body.code().trim().toUpperCase(Locale.ROOT);
  }

  private static String name(CodeName body) {
    if (body == null || body.name() == null || body.name().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is required");
    }
    return body.name().trim();
  }

  private static String blank(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }

  private static String token(String value, String... allowed) {
    String token = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    for (String item : allowed) {
      if (item.equals(token)) {
        return token;
      }
    }
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use one of " + String.join(", ", allowed));
  }
}
