package com.shopmanagement.crmservice.integration.meta;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.shopmanagement.crmservice.integration.meta.MetaGraphClient.LeadField;
import com.shopmanagement.crmservice.persistence.entity.CrmFieldMapEntity;
import com.shopmanagement.crmservice.support.PhoneCandidates;

/** Maps Meta field_data onto a lead. Unmapped answers stay on the touch. */
public final class MetaLeadMapper {

  public record Mapped(
      String name,
      String phone,
      String email,
      String title,
      String course,
      Map<String, Object> extras) {}

  private MetaLeadMapper() {}

  public static Mapped map(List<LeadField> fields, List<CrmFieldMapEntity> maps, String defaultCourse) {
    Map<String, String> targets = new LinkedHashMap<>();
    if (maps == null || maps.isEmpty()) {
      defaults(targets);
    } else {
      for (CrmFieldMapEntity row : maps) {
        targets.put(row.getExternalKey().trim().toLowerCase(Locale.ROOT), row.getTarget());
      }
    }
    String name = null;
    String phone = null;
    String email = null;
    String title = null;
    String course = null;
    Map<String, Object> extras = new LinkedHashMap<>();
    if (fields != null) {
      for (LeadField field : fields) {
        if (field == null || field.name() == null || field.values() == null || field.values().isEmpty()) {
          continue;
        }
        String value = field.values().get(0);
        String target = targets.get(field.name().trim().toLowerCase(Locale.ROOT));
        if (target == null) {
          extras.put(field.name().trim(), value);
          continue;
        }
        switch (target) {
          case "CONTACT_NAME" -> name = name == null ? value : name;
          case "CONTACT_PHONE" -> phone = phone == null ? PhoneCandidates.e164(value) : phone;
          case "CONTACT_EMAIL" -> email = email == null ? value.toLowerCase(Locale.ROOT) : email;
          case "LEAD_TITLE" -> title = title == null ? value : title;
          case "COURSE" -> course = course == null ? value : course;
          default -> extras.put(field.name().trim(), value);
        }
      }
    }
    if (course == null && defaultCourse != null && !defaultCourse.isBlank()) {
      course = defaultCourse.trim();
    }
    if (title == null) {
      title = name == null ? "Facebook lead" : name;
    }
    return new Mapped(name, phone, email, title, course, extras);
  }

  private static void defaults(Map<String, String> targets) {
    targets.put("full_name", "CONTACT_NAME");
    targets.put("name", "CONTACT_NAME");
    targets.put("phone_number", "CONTACT_PHONE");
    targets.put("phone", "CONTACT_PHONE");
    targets.put("email", "CONTACT_EMAIL");
    targets.put("course", "COURSE");
  }
}
