package org.congcong.algomentor.mentor.application.profile;

import java.util.EnumSet;
import java.util.Set;

/** Java 画像范围矩阵，与数据库 ck_learner_profile_entry_scope 保持一致。 */
public final class LearnerProfileContract {

  private static final Set<LearnerProfileDimension> DECLARED_DIMENSIONS = EnumSet.of(
      LearnerProfileDimension.LEARNER_BACKGROUND,
      LearnerProfileDimension.GOALS_AND_INTENTS,
      LearnerProfileDimension.TIME_AND_RESOURCE_CONSTRAINTS,
      LearnerProfileDimension.LEARNING_AND_INTERACTION_PREFERENCES,
      LearnerProfileDimension.SELF_ABILITY_ASSESSMENT);
  private static final Set<LearnerProfileDimension> GENERAL_DIMENSIONS = EnumSet.of(
      LearnerProfileDimension.PROBLEM_SOLVING_APPROACH,
      LearnerProfileDimension.IMPLEMENTATION_AND_ERROR_PATTERN,
      LearnerProfileDimension.LEARNING_INTERACTION_AND_INDEPENDENCE,
      LearnerProfileDimension.REVIEW_AND_GROWTH_PERFORMANCE);

  private LearnerProfileContract() {
  }

  public static boolean isValidScope(
      LearnerProfileEntryKind kind,
      LearnerProfileDimension dimension,
      Long tagId) {
    if (kind == null || dimension == null) {
      return false;
    }
    return switch (kind) {
      case DECLARED_FACT -> tagId == null && DECLARED_DIMENSIONS.contains(dimension);
      case GENERAL_OBSERVATION -> tagId == null && GENERAL_DIMENSIONS.contains(dimension);
      case TAG_ASSESSMENT -> tagId != null && tagId > 0 && dimension == LearnerProfileDimension.TAG_MASTERY;
    };
  }

  public static Set<LearnerProfileDimension> declaredDimensions() {
    return Set.copyOf(DECLARED_DIMENSIONS);
  }

  public static Set<LearnerProfileDimension> generalDimensions() {
    return Set.copyOf(GENERAL_DIMENSIONS);
  }
}
