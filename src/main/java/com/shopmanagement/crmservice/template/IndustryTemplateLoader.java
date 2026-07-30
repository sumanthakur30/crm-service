package com.shopmanagement.crmservice.template;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class IndustryTemplateLoader {

  private final ObjectMapper mapper = new ObjectMapper();
  private final Map<String, IndustryTemplate> cache = new ConcurrentHashMap<>();

  public IndustryTemplate get(String code) {
    String key = (code == null || code.isBlank() ? "GENERIC" : code.trim().toUpperCase(Locale.ROOT));
    return cache.computeIfAbsent(key, this::load);
  }

  public List<String> listCodes() {
    ensureLoaded();
    return cache.keySet().stream().sorted().toList();
  }

  private IndustryTemplate load(String code) {
    try {
      Resource resource =
          new PathMatchingResourcePatternResolver()
              .getResource("classpath:crm-templates/" + code + ".json");
      if (!resource.exists()) {
        if (!"GENERIC".equals(code)) {
          return load("GENERIC");
        }
        throw new IllegalStateException("Missing GENERIC template");
      }
      try (InputStream in = resource.getInputStream()) {
        JsonNode root = mapper.readTree(in);
        return new IndustryTemplate(
            text(root, "code", code),
            text(root, "name", code),
            parsePipeline(root.get("leadPipeline")),
            parsePipeline(root.get("opportunityPipeline")));
      }
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to load CRM template " + code, ex);
    }
  }

  private void ensureLoaded() {
    try {
      Resource[] resources =
          new PathMatchingResourcePatternResolver().getResources("classpath:crm-templates/*.json");
      for (Resource r : resources) {
        String filename = r.getFilename();
        if (filename == null) {
          continue;
        }
        String code = filename.replace(".json", "").toUpperCase(Locale.ROOT);
        cache.computeIfAbsent(code, this::load);
      }
    } catch (Exception ex) {
      cache.computeIfAbsent("GENERIC", this::load);
    }
  }

  private IndustryTemplate.PipelineSpec parsePipeline(JsonNode node) {
    if (node == null || node.isNull()) {
      return new IndustryTemplate.PipelineSpec("SALES", "Sales Pipeline", List.of());
    }
    List<IndustryTemplate.StageSpec> stages = new ArrayList<>();
    JsonNode arr = node.get("stages");
    if (arr != null && arr.isArray()) {
      for (JsonNode s : arr) {
        stages.add(
            new IndustryTemplate.StageSpec(
                text(s, "code", "STAGE"),
                text(s, "name", "Stage"),
                s.path("sortOrder").asInt(10),
                s.path("probability").asInt(0),
                s.path("won").asBoolean(false),
                s.path("lost").asBoolean(false)));
      }
    }
    return new IndustryTemplate.PipelineSpec(
        text(node, "code", "SALES"), text(node, "name", "Sales Pipeline"), stages);
  }

  private static String text(JsonNode node, String field, String fallback) {
    JsonNode v = node.get(field);
    if (v == null || v.isNull() || v.asText().isBlank()) {
      return fallback;
    }
    return v.asText().trim();
  }
}
