package org.congcong.algomentor.mentor.application.profile.claim.model;

import java.util.EnumSet;
import java.util.Set;

/** Claim 作用域、状态和容量的跨模块固定契约。 */
public final class LearnerMemoryClaimContract {

  public static final int CLAIM_TEXT_MAX_CHARS = 600;
  public static final int CLAIM_TEXT_TARGET_MAX_CHARS = 300;
  public static final int DECLARED_SCOPE_ACTIVE_LIMIT = 10;
  public static final int GENERAL_SCOPE_ACTIVE_LIMIT = 10;
  public static final int TAG_SCOPE_ACTIVE_LIMIT = 5;
  public static final int USER_ACTIVE_SOFT_LIMIT = 500;
  public static final int USER_ACTIVE_HARD_LIMIT = 1_000;

  private static final Set<Dimension> DECLARED_DIMENSIONS = EnumSet.of(
      Dimension.LEARNER_BACKGROUND,
      Dimension.GOALS_AND_INTENTS,
      Dimension.TIME_AND_RESOURCE_CONSTRAINTS,
      Dimension.LEARNING_AND_INTERACTION_PREFERENCES,
      Dimension.SELF_ABILITY_ASSESSMENT);
  private static final Set<Dimension> GENERAL_DIMENSIONS = EnumSet.of(
      Dimension.PROBLEM_SOLVING_APPROACH,
      Dimension.IMPLEMENTATION_AND_ERROR_PATTERN,
      Dimension.LEARNING_INTERACTION_AND_INDEPENDENCE,
      Dimension.REVIEW_AND_GROWTH_PERFORMANCE);

  private LearnerMemoryClaimContract() {
  }

  public enum Kind {
    DECLARED_FACT,
    GENERAL_OBSERVATION,
    TAG_ASSESSMENT
  }

  public enum Dimension {
    LEARNER_BACKGROUND,
    GOALS_AND_INTENTS,
    TIME_AND_RESOURCE_CONSTRAINTS,
    LEARNING_AND_INTERACTION_PREFERENCES,
    SELF_ABILITY_ASSESSMENT,
    PROBLEM_SOLVING_APPROACH,
    IMPLEMENTATION_AND_ERROR_PATTERN,
    LEARNING_INTERACTION_AND_INDEPENDENCE,
    REVIEW_AND_GROWTH_PERFORMANCE,
    TAG_MASTERY
  }

  public enum RevisionStatus {
    ACTIVE,
    SUPERSEDED,
    RETIRED,
    SUPPRESSED,
    REJECTED;

    public boolean isCurrent() {
      return this != SUPERSEDED;
    }
  }

  public enum Origin {
    USER_EXPLICIT,
    USER_CORRECTION,
    SYSTEM_DERIVED
  }

  public enum OperationAction {
    ADD,
    CONFIRM,
    REVISE,
    RETIRE
  }

  public enum CapacityState {
    NORMAL,
    SOFT_LIMIT,
    HARD_LIMIT
  }

  public static boolean isValidScope(Kind kind, Dimension dimension, Long tagId) {
    if (kind == null || dimension == null) {
      return false;
    }
    return switch (kind) {
      case DECLARED_FACT -> tagId == null && DECLARED_DIMENSIONS.contains(dimension);
      case GENERAL_OBSERVATION -> tagId == null && GENERAL_DIMENSIONS.contains(dimension);
      case TAG_ASSESSMENT -> tagId != null && tagId > 0 && dimension == Dimension.TAG_MASTERY;
    };
  }

  public static Set<Dimension> declaredDimensions() {
    return Set.copyOf(DECLARED_DIMENSIONS);
  }

  public static Set<Dimension> generalDimensions() {
    return Set.copyOf(GENERAL_DIMENSIONS);
  }

  static void requireValidScope(Kind kind, Dimension dimension, Long tagId) {
    if (!isValidScope(kind, dimension, tagId)) {
      throw new IllegalArgumentException("非法 learner memory claim scope。");
    }
  }
}
