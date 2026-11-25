package com.shopmanagement.crmservice.support;

import java.util.Locale;

/** Column names shared by the CSV wizard and the import shortcut. */
public final class ImportColumns {

  private ImportColumns() {}

  public static String suggest(String header) {
    String key = header == null ? "" : header.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    return switch (key) {
      case "phone", "mobile", "mobile_number", "phone_number", "contact_number" -> "phone";
      case "email", "email_id" -> "email";
      case "course", "program", "class" -> "course";
      case "source", "source_code", "lead_source" -> "source";
      case "display_name", "contact", "contact_name", "student_name", "full_name" -> "displayName";
      case "title", "lead_title", "name", "lead_name" -> "title";
      default -> "ignore";
    };
  }

  /** Same normalizer as website forms. An unparseable value is kept so the row is not dropped. */
  public static String phone(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String e164 = PhoneCandidates.e164(raw);
    return e164 != null ? e164 : raw.trim();
  }
}
