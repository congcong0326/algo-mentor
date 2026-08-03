package org.congcong.algomentor.mentor.application.learningplan.personalization;

import java.util.Map;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;

/** 可放入单次 Agent input 的个性化上下文快照。 */
public record LearningPlanPersonalizationSnapshot(
    boolean enabled,
    LearningPlanPersonalizationContext context,
    String promptText,
    int tokenEstimate,
    boolean trimmed,
    Map<LearningPlanPersonalizationSource, LearningPlanPersonalizationSourceOutcome> sourceOutcomes
) {

  public LearningPlanPersonalizationSnapshot {
    if (context == null) {
      throw new IllegalArgumentException("personalization context must not be null");
    }
    promptText = promptText == null ? "" : promptText;
    if (tokenEstimate < 0 || tokenEstimate > LearningPlanPersonalizationConstants.TOKEN_BUDGET) {
      throw new IllegalArgumentException("personalization token estimate is outside the fixed budget");
    }
    sourceOutcomes = sourceOutcomes == null ? Map.of() : Map.copyOf(sourceOutcomes);
    for (LearningPlanPersonalizationSource source : LearningPlanPersonalizationSource.values()) {
      if (!sourceOutcomes.containsKey(source) || sourceOutcomes.get(source) == null) {
        throw new IllegalArgumentException("personalization source outcomes must be complete");
      }
    }
  }

  public static LearningPlanPersonalizationSnapshot disabled(Instant generatedAt) {
    EnumMap<LearningPlanPersonalizationSource, LearningPlanPersonalizationSourceOutcome> outcomes =
        new EnumMap<>(LearningPlanPersonalizationSource.class);
    for (LearningPlanPersonalizationSource source : LearningPlanPersonalizationSource.values()) {
      outcomes.put(source, LearningPlanPersonalizationSourceOutcome.DISABLED);
    }
    return new LearningPlanPersonalizationSnapshot(
        false,
        new LearningPlanPersonalizationContext(List.of(), List.of(), List.of(), List.of(), null, null, generatedAt),
        "",
        0,
        false,
        outcomes);
  }
}
