package com.shopmanagement.crmservice.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopmanagement.crmservice.api.CrmLeadApi.LeadUpsert;
import com.shopmanagement.crmservice.persistence.entity.CrmAttributionEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmContactEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmFieldDefEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmFieldValueEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmLeadContactEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmLeadEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmTouchEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmWorkspaceEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmAttributionRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmContactRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmFieldDefRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmFieldValueRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmLeadContactRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmLeadRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmTouchRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmWorkspaceRepository;
import com.shopmanagement.crmservice.support.PhoneCandidates;

/**
 * Writes the contact link, course value, and an insert-only touch. A repeat enquiry adds a touch
 * and does not change the lead's owner or stored fields.
 */
@Service
public class LeadCaptureService {

  private final CrmLeadRepository leadRepository;
  private final CrmContactRepository contactRepository;
  private final CrmLeadContactRepository leadContactRepository;
  private final CrmTouchRepository touchRepository;
  private final CrmAttributionRepository attributionRepository;
  private final CrmFieldDefRepository fieldDefRepository;
  private final CrmFieldValueRepository fieldValueRepository;
  private final CrmWorkspaceRepository workspaceRepository;
  private final boolean backfillEnabled;

  public LeadCaptureService(
      CrmLeadRepository leadRepository,
      CrmContactRepository contactRepository,
      CrmLeadContactRepository leadContactRepository,
      CrmTouchRepository touchRepository,
      CrmAttributionRepository attributionRepository,
      CrmFieldDefRepository fieldDefRepository,
      CrmFieldValueRepository fieldValueRepository,
      CrmWorkspaceRepository workspaceRepository,
      @Value("${crm.backfill.enabled:false}") boolean backfillEnabled) {
    this.leadRepository = leadRepository;
    this.contactRepository = contactRepository;
    this.leadContactRepository = leadContactRepository;
    this.touchRepository = touchRepository;
    this.attributionRepository = attributionRepository;
    this.fieldDefRepository = fieldDefRepository;
    this.fieldValueRepository = fieldValueRepository;
    this.workspaceRepository = workspaceRepository;
    this.backfillEnabled = backfillEnabled;
  }

  @Transactional
  public void recordNew(CrmLeadEntity lead, LeadUpsert body) {
    Long branchId = branchOf(body);
    if (branchId != null && lead.getBranchId() == null) {
      lead.setBranchId(branchId);
      if (lead.getId() != null) {
        leadRepository.save(lead);
      }
    }
    boolean education = educationTenant(lead.getTenantId());
    linkContact(lead, education);
    storeCourse(lead);
    insertTouch(lead, body, false);
  }

  /** Another enquiry for someone we already have. The existing lead row stays as it was. */
  @Transactional
  public void recordRepeat(CrmLeadEntity lead, LeadUpsert body) {
    insertTouch(lead, body, true);
  }

  @Transactional
  public void backfillIfEnabled(String tenantId) {
    if (!backfillEnabled || tenantId == null || tenantId.isBlank()) {
      return;
    }
    boolean education = educationTenant(tenantId);
    List<CrmLeadEntity> missing =
        leadRepository.findByTenantIdAndContactIdIsNullAndDeletedAtIsNull(tenantId, PageRequest.of(0, 50));
    for (CrmLeadEntity lead : missing) {
      linkContact(lead, education);
      if (!touchRepository.existsByTenantIdAndLeadId(tenantId, lead.getId())) {
        insertTouch(lead, null, false);
      }
    }
  }

  private void linkContact(CrmLeadEntity lead, boolean education) {
    String e164 = PhoneCandidates.e164(lead.getPhone());
    CrmContactEntity contact = null;
    if (lead.getContactId() != null) {
      contact =
          contactRepository
              .findByTenantIdAndIdAndDeletedAtIsNull(lead.getTenantId(), lead.getContactId())
              .orElse(null);
    }
    if (contact == null && e164 != null) {
      contact =
          contactRepository
              .findByTenantIdAndPhoneE164AndDeletedAtIsNull(lead.getTenantId(), e164)
              .orElse(null);
    }
    if (contact == null) {
      contact = new CrmContactEntity();
      contact.setTenantId(lead.getTenantId());
      String name = lead.getDisplayName();
      if (name == null || name.isBlank()) {
        name = lead.getTitle() == null || lead.getTitle().isBlank() ? "Enquiry" : lead.getTitle().trim();
      }
      contact.setDisplayName(name.length() > 256 ? name.substring(0, 256) : name);
      contact.setPhone(lead.getPhone());
      contact.setPhoneRaw(lead.getPhone());
      contact.setPhoneE164(e164);
      contact.setEmail(lead.getEmail());
      contact.setEmailNormalized(normalizeEmail(lead.getEmail()));
      contact.setContactKind(education ? "STUDENT" : "OTHER");
      contact.setDoNotContact(false);
      contact = contactRepository.save(contact);
    }
    if (lead.getContactId() == null) {
      lead.setContactId(contact.getId());
      leadRepository.save(lead);
    }
    String role = education ? "STUDENT" : "SELF";
    Optional<CrmLeadContactEntity> link =
        leadContactRepository.findByTenantIdAndLeadIdAndContactIdAndRoleAndDeletedAtIsNull(
            lead.getTenantId(), lead.getId(), contact.getId(), role);
    if (link.isEmpty()) {
      CrmLeadContactEntity row = new CrmLeadContactEntity();
      row.setTenantId(lead.getTenantId());
      row.setLeadId(lead.getId());
      row.setContactId(contact.getId());
      row.setRole(role);
      row.setPrimaryContact(true);
      leadContactRepository.save(row);
    }
  }

  private void storeCourse(CrmLeadEntity lead) {
    String course = courseOf(lead);
    if (course == null) {
      return;
    }
    CrmFieldDefEntity def =
        fieldDefRepository
            .findByTenantIdAndObjectTypeAndCodeAndDeletedAtIsNull(lead.getTenantId(), "LEAD", "course")
            .orElseGet(() -> courseDef(lead.getTenantId()));
    CrmFieldValueEntity value =
        fieldValueRepository
            .findByTenantIdAndObjectTypeAndObjectIdAndFieldDefIdAndDeletedAtIsNull(
                lead.getTenantId(), "LEAD", lead.getId(), def.getId())
            .orElseGet(CrmFieldValueEntity::new);
    if (value.getId() == null) {
      value.setTenantId(lead.getTenantId());
      value.setObjectType("LEAD");
      value.setObjectId(lead.getId());
      value.setFieldDefId(def.getId());
    }
    value.setValueText(course);
    value.touch();
    fieldValueRepository.save(value);
  }

  private CrmFieldDefEntity courseDef(String tenantId) {
    CrmFieldDefEntity def = new CrmFieldDefEntity();
    def.setTenantId(tenantId);
    def.setObjectType("LEAD");
    def.setCode("course");
    def.setLabel("Course");
    def.setDataType("TEXT");
    def.setSortOrder(10);
    return fieldDefRepository.save(def);
  }

  private void insertTouch(CrmLeadEntity lead, LeadUpsert body, boolean repeat) {
    String source = sourceOf(lead, body);
    String provider = providerOf(source);
    String externalId = externalId(body);
    if (externalId != null
        && touchRepository
            .findByTenantIdAndProviderAndExternalId(lead.getTenantId(), provider, externalId)
            .isPresent()) {
      return;
    }
    CrmTouchEntity touch = new CrmTouchEntity();
    touch.setTenantId(lead.getTenantId());
    touch.setLeadId(lead.getId());
    touch.setContactId(lead.getContactId());
    touch.setSourceCode(source);
    touch.setSourceCategory(categoryOf(source));
    touch.setProvider(provider);
    touch.setExternalId(externalId);
    touch.setCampaignId(repeat && body != null && body.campaignId() != null ? body.campaignId() : lead.getCampaignId());
    Long branchId = branchOf(body);
    touch.setBranchId(branchId != null ? branchId : lead.getBranchId());
    touch.setRawEventId(rawEventId(body));
    Instant now = Instant.now();
    touch.setOccurredAt(now);
    touch.setReceivedAt(now);
    String course = repeat ? courseOf(body) : courseOf(lead);
    if (course == null) {
      course = courseOf(lead);
    }
    Map<String, Object> values = new LinkedHashMap<>();
    if (course != null) {
      values.put("course", course);
    }
    if (body != null && body.attributes() != null) {
      for (Map.Entry<String, Object> entry : body.attributes().entrySet()) {
        if (entry.getValue() == null || "branchId".equals(entry.getKey()) || "phoneRaw".equals(entry.getKey())) {
          continue;
        }
        if ("course".equals(entry.getKey())) {
          continue;
        }
        values.put(entry.getKey(), entry.getValue());
      }
    }
    if (!values.isEmpty()) {
      touch.setFieldValues(values);
    }
    try {
      touch = touchRepository.save(touch);
    } catch (DataIntegrityViolationException ex) {
      return;
    }
    saveAttribution(touch.getId(), lead, body, repeat);
  }

  private void saveAttribution(Long touchId, CrmLeadEntity lead, LeadUpsert body, boolean repeat) {
    String utmSource = repeat && body != null ? body.utmSource() : lead.getUtmSource();
    String utmMedium = repeat && body != null ? body.utmMedium() : lead.getUtmMedium();
    String utmCampaign = repeat && body != null ? body.utmCampaign() : lead.getUtmCampaign();
    String utmContent = repeat && body != null ? body.utmContent() : lead.getUtmContent();
    String utmTerm = repeat && body != null ? body.utmTerm() : lead.getUtmTerm();
    if (blank(utmSource) && blank(utmMedium) && blank(utmCampaign) && blank(utmContent) && blank(utmTerm)) {
      return;
    }
    CrmAttributionEntity row = new CrmAttributionEntity();
    row.setTouchId(touchId);
    row.setUtmSource(trim(utmSource));
    row.setUtmMedium(trim(utmMedium));
    row.setUtmCampaign(trim(utmCampaign));
    row.setUtmContent(trim(utmContent));
    row.setUtmTerm(trim(utmTerm));
    attributionRepository.save(row);
  }

  private boolean educationTenant(String tenantId) {
    return workspaceRepository
        .findByTenantIdAndDeletedAtIsNull(tenantId)
        .map(CrmWorkspaceEntity::getTemplateCode)
        .map(code -> "EDUCATION".equalsIgnoreCase(code))
        .orElse(false);
  }

  private static Long branchOf(LeadUpsert body) {
    if (body == null || body.attributes() == null) {
      return null;
    }
    return asLong(body.attributes().get("branchId"));
  }

  private static Long rawEventId(LeadUpsert body) {
    if (body == null || body.externalRefs() == null) {
      return null;
    }
    return asLong(body.externalRefs().get("rawEventId"));
  }

  private static Long asLong(Object value) {
    if (value instanceof Number number) {
      return number.longValue();
    }
    if (value == null) {
      return null;
    }
    try {
      String text = String.valueOf(value).trim();
      return text.isEmpty() ? null : Long.parseLong(text);
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private static String courseOf(LeadUpsert body) {
    if (body == null || body.attributes() == null) {
      return null;
    }
    Object value = body.attributes().get("course");
    if (value == null) {
      return null;
    }
    String text = String.valueOf(value).trim();
    return text.isEmpty() ? null : text;
  }

  private static String courseOf(CrmLeadEntity lead) {
    if (lead.getAttributes() == null) {
      return null;
    }
    Object value = lead.getAttributes().get("course");
    if (value == null) {
      return null;
    }
    String text = String.valueOf(value).trim();
    return text.isEmpty() ? null : text;
  }

  private static String sourceOf(CrmLeadEntity lead, LeadUpsert body) {
    String raw = body != null && body.sourceCode() != null ? body.sourceCode() : lead.getSourceCode();
    if (raw == null || raw.isBlank()) {
      return "MANUAL";
    }
    String code = raw.trim().toUpperCase(Locale.ROOT);
    return code.length() <= 64 ? code : code.substring(0, 64);
  }

  static String providerOf(String source) {
    return switch (source) {
      case "META", "FACEBOOK", "FACEBOOK_LEAD_AD", "INSTAGRAM" -> "META";
      case "GOOGLE", "GOOGLE_LEAD_FORM" -> "GOOGLE";
      case "WEBSITE", "PORTAL" -> "WEB";
      case "WHATSAPP" -> "WHATSAPP";
      case "MISSED_CALL", "CALL_MISSED", "CALL_INBOUND" -> "TELEPHONY";
      case "IMPORT", "CSV" -> "FILE";
      default -> "USER";
    };
  }

  static String categoryOf(String source) {
    return switch (source) {
      case "META", "FACEBOOK", "FACEBOOK_LEAD_AD", "INSTAGRAM", "GOOGLE", "GOOGLE_LEAD_FORM", "AD", "CAMPAIGN" ->
          "PAID";
      case "REFERRAL" -> "PARTNER";
      case "WALKIN", "WALK_IN", "VISIT", "CALL", "MANUAL", "IMPORT", "CSV" -> "OFFLINE";
      default -> "ORGANIC";
    };
  }

  private static String externalId(LeadUpsert body) {
    if (body == null || body.externalRefs() == null) {
      return null;
    }
    for (String key : List.of("externalId", "leadgen_id", "lead_id")) {
      Object value = body.externalRefs().get(key);
      if (value != null && !String.valueOf(value).isBlank()) {
        String text = String.valueOf(value).trim();
        return text.length() <= 128 ? text : text.substring(0, 128);
      }
    }
    return null;
  }

  private static String normalizeEmail(String email) {
    if (email == null || email.isBlank()) {
      return null;
    }
    return email.trim().toLowerCase(Locale.ROOT);
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private static String trim(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
