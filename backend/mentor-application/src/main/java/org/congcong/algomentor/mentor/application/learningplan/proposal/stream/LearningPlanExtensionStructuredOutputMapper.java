package org.congcong.algomentor.mentor.application.learningplan.proposal.stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanContentLocale;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftMetadataKeys;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanPhaseDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemCandidate;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemCatalog;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemDraft;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanExtensionDraft;

/**
 * 把模型结构化输出映射为学习计划扩展草案。
 */
public class LearningPlanExtensionStructuredOutputMapper {

  private final ObjectMapper objectMapper;
  private final LearningPlanProblemCatalog problemCatalog;

  public LearningPlanExtensionStructuredOutputMapper(ObjectMapper objectMapper) {
    this(objectMapper, null);
  }

  public LearningPlanExtensionStructuredOutputMapper(
      ObjectMapper objectMapper,
      LearningPlanProblemCatalog problemCatalog
  ) {
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    this.problemCatalog = problemCatalog;
  }

  public LearningPlanExtensionDraft map(JsonNode node) {
    return map(node, LearningPlanContentLocale.ZH_CN);
  }

  public LearningPlanExtensionDraft map(JsonNode node, LearningPlanContentLocale contentLocale) {
    if (node == null || node.isNull()) {
      throw new LearningPlanException("LEARNING_PLAN_EXTENSION_STRUCTURED_OUTPUT_INVALID", "模型未返回扩展提案。");
    }
    try {
      LearningPlanExtensionDraft raw = objectMapper.treeToValue(node, LearningPlanExtensionDraft.class);
      return problemCatalog == null ? raw : normalize(raw, contentLocale);
    } catch (JsonProcessingException exception) {
      throw new LearningPlanException("LEARNING_PLAN_EXTENSION_STRUCTURED_OUTPUT_INVALID", "扩展提案结构化结果解析失败。");
    }
  }

  private LearningPlanExtensionDraft normalize(
      LearningPlanExtensionDraft raw,
      LearningPlanContentLocale contentLocale
  ) {
    String locale = contentLocale.languageTag();
    List<LearningPlanPhaseDraft> phases = new ArrayList<>();
    for (LearningPlanPhaseDraft phase : raw.newPhases()) {
      List<LearningPlanProblemDraft> problems = new ArrayList<>();
      int sortOrder = 1;
      for (LearningPlanProblemDraft problem : phase.problems()) {
        LearningPlanProblemCandidate candidate = problemCatalog.findBySlug(problem.slug(), locale).orElse(null);
        if (candidate != null) {
          problems.add(LearningPlanProblemDraft.fromCandidate(candidate, sortOrder++, problem.reason()));
        }
      }
      phases.add(new LearningPlanPhaseDraft(
          phase.phaseIndex(),
          phase.title(),
          phase.durationWeeks(),
          phase.focus(),
          problems));
    }
    Map<String, Object> metadata = new LinkedHashMap<>(raw.metadata());
    metadata.put(LearningPlanDraftMetadataKeys.CONTENT_LOCALE, contentLocale.languageTag());
    return new LearningPlanExtensionDraft(raw.summary(), phases, metadata);
  }

}
