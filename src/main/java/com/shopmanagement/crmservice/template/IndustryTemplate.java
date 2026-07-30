package com.shopmanagement.crmservice.template;

import java.util.List;

public record IndustryTemplate(
    String code,
    String name,
    PipelineSpec leadPipeline,
    PipelineSpec opportunityPipeline) {

  public record PipelineSpec(String code, String name, List<StageSpec> stages) {}

  public record StageSpec(
      String code, String name, int sortOrder, int probability, boolean won, boolean lost) {}
}
