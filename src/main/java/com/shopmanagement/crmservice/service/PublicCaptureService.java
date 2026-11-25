package com.shopmanagement.crmservice.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopmanagement.crmservice.api.CrmCampaignApi.PublicCaptureRequest;
import com.shopmanagement.crmservice.persistence.repo.CrmWebFormRepository;
import com.shopmanagement.crmservice.service.CapturePipe.CaptureResult;
import com.shopmanagement.crmservice.service.WebFormCaptureService.Result;

/** Public capture URL. A form key uses the field map. Any other key stays a campaign capture. */
@Service
public class PublicCaptureService {

  private final CrmWebFormRepository formRepository;
  private final WebFormCaptureService webFormCaptureService;
  private final CampaignService campaignService;

  public PublicCaptureService(
      CrmWebFormRepository formRepository,
      WebFormCaptureService webFormCaptureService,
      CampaignService campaignService) {
    this.formRepository = formRepository;
    this.webFormCaptureService = webFormCaptureService;
    this.campaignService = campaignService;
  }

  @Transactional
  public Result capture(String publicKey, Map<String, Object> body, String idempotencyKey) {
    Map<String, Object> fields = body == null ? new LinkedHashMap<>() : new LinkedHashMap<>(body);
    String key = firstKey(idempotencyKey, fields);
    var form = formRepository.findByPublicKeyAndDeletedAtIsNull(publicKey);
    if (form.isPresent()) {
      return webFormCaptureService.submit(form.get(), fields, key);
    }
    CaptureResult campaign =
        campaignService.captureTracked(publicKey, toRequest(fields), key, fields);
    return new Result(campaign.lead(), campaign.duplicate(), false);
  }

  private static PublicCaptureRequest toRequest(Map<String, Object> fields) {
    return new PublicCaptureRequest(
        text(fields, "title"),
        text(fields, "displayName", "display_name", "name"),
        text(fields, "companyName", "company_name"),
        text(fields, "email"),
        text(fields, "phone"),
        text(fields, "utmSource", "utm_source"),
        text(fields, "utmMedium", "utm_medium"),
        text(fields, "utmCampaign", "utm_campaign"),
        text(fields, "utmContent", "utm_content"),
        text(fields, "utmTerm", "utm_term"),
        map(fields.get("attributes")));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> map(Object value) {
    if (value instanceof Map<?, ?> raw) {
      Map<String, Object> out = new LinkedHashMap<>();
      raw.forEach((k, v) -> out.put(String.valueOf(k), v));
      return out;
    }
    return null;
  }

  private static String firstKey(String header, Map<String, Object> fields) {
    if (header != null && !header.isBlank()) {
      return header.trim();
    }
    String fromBody = text(fields, "idempotencyKey", "Idempotency-Key");
    fields.remove("idempotencyKey");
    fields.remove("Idempotency-Key");
    return fromBody;
  }

  private static String text(Map<String, Object> fields, String... keys) {
    for (String key : keys) {
      Object value = fields.get(key);
      if (value != null && !String.valueOf(value).isBlank()) {
        return String.valueOf(value).trim();
      }
    }
    return null;
  }
}
