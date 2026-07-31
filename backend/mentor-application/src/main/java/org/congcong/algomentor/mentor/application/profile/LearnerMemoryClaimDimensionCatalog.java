package org.congcong.algomentor.mentor.application.profile;

import java.util.EnumSet;
import java.util.Set;

/** Learner memory claim 维度目录，与数据库 claim scope 约束保持一致。 */
public final class LearnerMemoryClaimDimensionCatalog {

  private static final Set<LearnerMemoryClaimDimension> DECLARED_DIMENSIONS = EnumSet.of(
      LearnerMemoryClaimDimension.LEARNER_BACKGROUND,
      LearnerMemoryClaimDimension.GOALS_AND_INTENTS,
      LearnerMemoryClaimDimension.TIME_AND_RESOURCE_CONSTRAINTS,
      LearnerMemoryClaimDimension.LEARNING_AND_INTERACTION_PREFERENCES,
      LearnerMemoryClaimDimension.SELF_ABILITY_ASSESSMENT);
  private static final Set<LearnerMemoryClaimDimension> GENERAL_DIMENSIONS = EnumSet.of(
      LearnerMemoryClaimDimension.PROBLEM_SOLVING_APPROACH,
      LearnerMemoryClaimDimension.IMPLEMENTATION_AND_ERROR_PATTERN,
      LearnerMemoryClaimDimension.LEARNING_INTERACTION_AND_INDEPENDENCE,
      LearnerMemoryClaimDimension.REVIEW_AND_GROWTH_PERFORMANCE);

  private LearnerMemoryClaimDimensionCatalog() {
  }

  public static Set<LearnerMemoryClaimDimension> declaredDimensions() {
    return Set.copyOf(DECLARED_DIMENSIONS);
  }

  public static Set<LearnerMemoryClaimDimension> generalDimensions() {
    return Set.copyOf(GENERAL_DIMENSIONS);
  }
}
