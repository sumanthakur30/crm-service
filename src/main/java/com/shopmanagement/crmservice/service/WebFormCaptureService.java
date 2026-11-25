package com.shopmanagement.crmservice.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.shopmanagement.crmservice.api.CrmLeadApi.LeadUpsert;
import com.shopmanagement.crmservice.filter.TenantContextFilter;
import com.shopmanagement.crmservice.persistence.entity.CrmFieldMapEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmInboundEventEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmWebFormEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmFieldMapRepository;
import com.shopmanagement.crmservice.service.CapturePipe.CaptureResult;
import com.shopmanagement.crmservice.support.PhoneCandidates;

/** Maps a website form onto the shared capture pipe. Does not set an owner. */
@Service
public class WebFormCaptureService {

  public record Result(com.shopmanagement.crmservice.api.CrmLeadApi.LeadResponse lead, boolean duplicate, boolean dropped) {}

  private final CrmFieldMapRepository fieldMapRepository;
  private final CapturePipe capturePipe;
  private final CrmLeadService leadService;

  public WebFormCaptureService(
      CrmFieldMapRepository fieldMapRepository, CapturePipe capturePipe, CrmLeadService leadService) {
    this.fieldMapRepository = fieldMapRepository;
    this.capturePipe = capturePipe;
    this.leadService = leadService;
  }

  @Transactional
  public Result submit(CrmWebFormEntity form, Map<String, Object> rawBody, String idempotencyKey) {
    if (form == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Form not found");
    }
    Map<String, Object> body = copy(rawBody);
    String honeypot = form.getHoneypotField() == null ? "company_website" : form.getHoneypotField();
    Object trap = body.remove(honeypot);
    if (trap != null && !String.valueOf(trap).isBlank()) {
      return new Result(null, false, true);
    }
    body.remove("idempotencyKey");
    body.remove("Idempotency-Key");
    TenantContextFilter.bindTenant(form.getTenantId());
    Mapped mapped = map(form, body);
    if (mapped.phoneE164 == null && mapped.email == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A mobile or email is required");
    }
    String key = idempotencyKey;
    CaptureResult result =
        capturePipe.run(
            form.getTenantId(),
            key,
            body,
            form.getCampaignId(),
            form.getBranchId(),
            event -> leadService.create(upsert(form, mapped, key, event), true));
    return new Result(result.lead(), result.duplicate(), false);
  }

  private LeadUpsert upsert(CrmWebFormEntity form, Mapped mapped, String idempotencyKey, CrmInboundEventEntity event) {
    Map<String, Object> attributes = new LinkedHashMap<>(mapped.extras);
    if (mapped.course != null) {
      attributes.put("course", mapped.course);
    }
    if (form.getBranchId() != null) {
      attributes.put("branchId", form.getBranchId());
    }
    if (mapped.phoneRaw != null) {
      attributes.put("phoneRaw", mapped.phoneRaw);
    }
    if ("TEST".equalsIgnoreCase(form.getStatus())) {
      attributes.put("test", true);
    }
    Map<String, Object> refs = new LinkedHashMap<>();
    if (idempotencyKey != null && !idempotencyKey.isBlank()) {
      String text = idempotencyKey.trim();
      refs.put("externalId", text.length() <= 128 ? text : text.substring(0, 128));
    }
    if (event != null && event.getId() != null) {
      refs.put("rawEventId", event.getId());
    }
    String title = first(mapped.title, mapped.name, "Website enquiry");
    return new LeadUpsert(
        title,
        mapped.name,
        null,
        mapped.email,
        mapped.phoneE164,
        "WEBSITE",
        "OPEN",
        "MEDIUM",
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        attributes,
        refs,
        form.getPublicKey(),
        form.getCampaignId(),
        mapped.utmSource,
        mapped.utmMedium,
        mapped.utmCampaign,
        mapped.utmContent,
        mapped.utmTerm,
        null,
        null);
  }

  private Mapped map(CrmWebFormEntity form, Map<String, Object> body) {
    List<CrmFieldMapEntity> rows = fieldMapRepository.findByFormIdOrderByIdAsc(form.getId());
    Map<String, String> targets = new LinkedHashMap<>();
    if (rows.isEmpty()) {
      defaults(targets);
    } else {
      for (CrmFieldMapEntity row : rows) {
        targets.put(row.getExternalKey().trim().toLowerCase(Locale.ROOT), row.getTarget());
      }
    }
    Mapped mapped = new Mapped();
    mapped.utmSource = text(body, "utm_source", "utmSource");
    mapped.utmMedium = text(body, "utm_medium", "utmMedium");
    mapped.utmCampaign = text(body, "utm_campaign", "utmCampaign");
    mapped.utmContent = text(body, "utm_content", "utmContent");
    mapped.utmTerm = text(body, "utm_term", "utmTerm");
    for (Map.Entry<String, Object> entry : body.entrySet()) {
      String key = entry.getKey();
      if (key == null || isUtm(key)) {
        continue;
      }
      String value = entry.getValue() == null ? null : String.valueOf(entry.getValue()).trim();
      if (value == null || value.isEmpty()) {
        continue;
      }
      String target = targets.get(key.trim().toLowerCase(Locale.ROOT));
      if (target == null) {
        mapped.extras.put(key.trim(), value);
        continue;
      }
      switch (target) {
        case "CONTACT_PHONE" -> {
          if (mapped.phoneRaw == null) {
            mapped.phoneRaw = value;
            mapped.phoneE164 = PhoneCandidates.e164(value);
          }
        }
        case "CONTACT_EMAIL" -> {
          if (mapped.email == null) {
            mapped.email = value.toLowerCase(Locale.ROOT);
          }
        }
        case "CONTACT_NAME" -> {
          if (mapped.name == null) {
            mapped.name = value;
          }
        }
        case "LEAD_TITLE" -> {
          if (mapped.title == null) {
            mapped.title = value;
          }
        }
        case "COURSE" -> {
          if (mapped.course == null) {
            mapped.course = value;
          }
        }
        default -> mapped.extras.put(key.trim(), value);
      }
    }
    if (mapped.course == null && form.getDefaultCourse() != null && !form.getDefaultCourse().isBlank()) {
      mapped.course = form.getDefaultCourse().trim();
    }
    return mapped;
  }

  private static void defaults(Map<String, String> targets) {
    targets.put("phone", "CONTACT_PHONE");
    targets.put("mobile", "CONTACT_PHONE");
    targets.put("mobile_number", "CONTACT_PHONE");
    targets.put("phone_number", "CONTACT_PHONE");
    targets.put("email", "CONTACT_EMAIL");
    targets.put("email_id", "CONTACT_EMAIL");
    targets.put("name", "CONTACT_NAME");
    targets.put("full_name", "CONTACT_NAME");
    targets.put("displayname", "CONTACT_NAME");
    targets.put("display_name", "CONTACT_NAME");
    targets.put("title", "LEAD_TITLE");
    targets.put("course", "COURSE");
    targets.put("program", "COURSE");
  }

  private static boolean isUtm(String key) {
    String normalized = key.trim().toLowerCase(Locale.ROOT).replace("_", "");
    return normalized.equals("utmsource")
        || normalized.equals("utmmedium")
        || normalized.equals("utmcampaign")
        || normalized.equals("utmcontent")
        || normalized.equals("utmterm");
  }

  private static String text(Map<String, Object> body, String... keys) {
    for (String key : keys) {
      Object value = body.get(key);
      if (value != null && !String.valueOf(value).isBlank()) {
        return String.valueOf(value).trim();
      }
    }
    return null;
  }

  private static String first(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value.trim();
      }
    }
    return "Website enquiry";
  }

  private static Map<String, Object> copy(Map<String, Object> raw) {
    return raw == null ? new LinkedHashMap<>() : new LinkedHashMap<>(raw);
  }

  private static final class Mapped {
    private String phoneRaw;
    private String phoneE164;
    private String email;
    private String name;
    private String title;
    private String course;
    private String utmSource;
    private String utmMedium;
    private String utmCampaign;
    private String utmContent;
    private String utmTerm;
    private final Map<String, Object> extras = new LinkedHashMap<>();
  }
}
