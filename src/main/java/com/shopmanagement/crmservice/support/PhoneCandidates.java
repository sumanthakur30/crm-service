package com.shopmanagement.crmservice.support;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Phone strings that should hit the same indexed equality lookup. */
public final class PhoneCandidates {

  private PhoneCandidates() {}

  public static List<String> of(String raw) {
    if (raw == null || raw.isBlank()) {
      return List.of();
    }
    Set<String> out = new LinkedHashSet<>();
    String trimmed = raw.trim();
    out.add(trimmed);
    String digits = trimmed.replaceAll("\\D", "");
    if (!digits.isEmpty()) {
      out.add(digits);
      if (digits.length() == 10) {
        out.add("91" + digits);
        out.add("+91" + digits);
      }
      if (digits.length() == 12 && digits.startsWith("91")) {
        out.add(digits.substring(2));
        out.add("+" + digits);
      }
      if (digits.length() > 10) {
        out.add(digits.substring(digits.length() - 10));
      }
    }
    return List.copyOf(new ArrayList<>(out));
  }
}
