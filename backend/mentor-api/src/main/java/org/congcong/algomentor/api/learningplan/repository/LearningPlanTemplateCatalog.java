package org.congcong.algomentor.api.learningplan.repository;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.congcong.algomentor.mentor.application.learningplan.template.LearningPlanTemplate;

/** 已按显示顺序组织、并提供 ID 索引的完整学习计划模板目录。 */
public record LearningPlanTemplateCatalog(
    List<LearningPlanTemplate> orderedTemplates,
    Map<String, LearningPlanTemplate> templatesById
) {

  public LearningPlanTemplateCatalog {
    orderedTemplates = List.copyOf(Objects.requireNonNull(orderedTemplates, "orderedTemplates must not be null"));
    templatesById = Map.copyOf(Objects.requireNonNull(templatesById, "templatesById must not be null"));
  }
}
