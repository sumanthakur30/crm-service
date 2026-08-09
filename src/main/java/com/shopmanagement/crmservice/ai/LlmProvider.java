package com.shopmanagement.crmservice.ai;

import java.util.Map;

/**
 * Pluggable LLM bridge (Sprint 10). Default {@link HeuristicLlmProvider}; optional {@link
 * HttpLlmProvider} when {@code crm.ai.provider=HTTP}.
 */
public interface LlmProvider {

  String code();

  /**
   * Optional remote completion. Empty / missing {@code body} means callers keep local heuristic
   * text.
   */
  Map<String, Object> complete(String task, String language, Map<String, Object> context);
}
