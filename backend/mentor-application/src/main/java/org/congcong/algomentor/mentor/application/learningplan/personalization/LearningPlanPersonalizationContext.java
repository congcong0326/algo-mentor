package org.congcong.algomentor.mentor.application.learningplan.personalization;

import java.time.Instant;
import java.util.List;

/** 单次学习计划生成使用的不可变个性化参考数据。 */
public record LearningPlanPersonalizationContext(
    List<String> declaredFacts,
    List<String> generalObservations,
    List<LearningPlanAbilityTagSummary> weakTags,
    List<LearningPlanAbilityTagSummary> strongTags,
    LearningPlanActiveProgressSummary activePlan,
    LearningPlanReviewLoadSummary reviewLoad,
    Instant generatedAt
) {

  public LearningPlanPersonalizationContext {
    declaredFacts = copyTexts(declaredFacts);
    generalObservations = copyTexts(generalObservations);
    weakTags = weakTags == null ? List.of() : List.copyOf(weakTags);
    strongTags = strongTags == null ? List.of() : List.copyOf(strongTags);
    if (generatedAt == null) {
      throw new IllegalArgumentException("generated at must not be null");
    }
  }

  public boolean isEmpty() {
    return declaredFacts.isEmpty()
        && generalObservations.isEmpty()
        && weakTags.isEmpty()
        && strongTags.isEmpty()
        && activePlan == null
        && reviewLoad == null;
  }

  private static List<String> copyTexts(List<String> values) {
    if (values == null || values.isEmpty()) {
      return List.of();
    }
    return values.stream()
        .map(value -> value == null ? "" : value.trim())
        .filter(value -> !value.isEmpty())
        .toList();
  }
}
