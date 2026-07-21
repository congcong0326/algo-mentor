package org.congcong.algomentor.mentor.application.profile.recall;

import java.util.List;
import org.congcong.algomentor.mentor.application.practice.PracticeChatPromptConstants;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileDimension;

/** 第一版场景白名单：只有 PRACTICE_CHAT 可按开关读取画像。 */
public final class LearnerProfilePolicyResolver {

  private static final List<LearnerProfileDimension> PRACTICE_DECLARED_DIMENSIONS = List.of(
      LearnerProfileDimension.LEARNER_BACKGROUND,
      LearnerProfileDimension.GOALS_AND_INTENTS,
      LearnerProfileDimension.TIME_AND_RESOURCE_CONSTRAINTS,
      LearnerProfileDimension.LEARNING_AND_INTERACTION_PREFERENCES,
      LearnerProfileDimension.SELF_ABILITY_ASSESSMENT);
  private static final List<LearnerProfileDimension> PRACTICE_GENERAL_DIMENSIONS = List.of(
      LearnerProfileDimension.PROBLEM_SOLVING_APPROACH,
      LearnerProfileDimension.IMPLEMENTATION_AND_ERROR_PATTERN);

  private final boolean practiceChatEnabled;
  private final int practiceChatMaxTokenBudget;

  public LearnerProfilePolicyResolver(boolean practiceChatEnabled, int practiceChatMaxTokenBudget) {
    if (practiceChatMaxTokenBudget < 1) {
      throw new IllegalArgumentException("Practice learner profile recall token budget must be positive");
    }
    this.practiceChatEnabled = practiceChatEnabled;
    this.practiceChatMaxTokenBudget = practiceChatMaxTokenBudget;
  }

  public LearnerProfilePolicy resolve(String scenario) {
    if (!PracticeChatPromptConstants.SCENARIO.equals(scenario)) {
      return LearnerProfilePolicy.disabled();
    }
    return new LearnerProfilePolicy(
        practiceChatEnabled,
        PRACTICE_DECLARED_DIMENSIONS,
        PRACTICE_GENERAL_DIMENSIONS,
        true,
        practiceChatMaxTokenBudget);
  }
}
