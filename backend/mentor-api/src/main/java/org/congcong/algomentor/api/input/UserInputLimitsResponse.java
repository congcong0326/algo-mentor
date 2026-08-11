package org.congcong.algomentor.api.input;

import org.congcong.algomentor.api.config.UserInputLimitProperties;

/** 前端可读取的当前用户输入上限快照。 */
public record UserInputLimitsResponse(
    ReviewNoteLimits reviewNote,
    LearningPlanCreateLimits learningPlanCreate,
    PracticeMessageLimits practiceMessage
) {

  public static UserInputLimitsResponse from(UserInputLimitProperties properties) {
    UserInputLimitProperties.ReviewNote review = properties.getReviewNote();
    UserInputLimitProperties.LearningPlanCreate plan = properties.getLearningPlanCreate();
    UserInputLimitProperties.PracticeMessage practice = properties.getPracticeMessage();
    return new UserInputLimitsResponse(
        new ReviewNoteLimits(
            review.getCoreIdeaMaxChars(),
            review.getDataStructureNotesMaxChars(),
            review.getAlgorithmNotesMaxChars(),
            review.getCustomItemMaxChars(),
            review.getCustomItemMaxCount(),
            review.getCustomComplexityMaxChars(),
            review.getEdgeCasesMaxChars(),
            review.getRequestMaxBytes().toBytes()),
        new LearningPlanCreateLimits(
            plan.getObjectiveMaxChars(),
            plan.getAdditionalConstraintsMaxChars(),
            plan.getDurationWeeksMax(),
            plan.getWeeklyHoursMax(),
            plan.getRequestMaxBytes().toBytes()),
        new PracticeMessageLimits(
            practice.getMessageMaxBytes().toBytes(),
            practice.getRequestMaxBytes().toBytes()));
  }

  public record ReviewNoteLimits(
      int coreIdeaMaxChars,
      int dataStructureNotesMaxChars,
      int algorithmNotesMaxChars,
      int customItemMaxChars,
      int customItemMaxCount,
      int customComplexityMaxChars,
      int edgeCasesMaxChars,
      long requestMaxBytes
  ) {
  }

  public record LearningPlanCreateLimits(
      int objectiveMaxChars,
      int additionalConstraintsMaxChars,
      int durationWeeksMax,
      int weeklyHoursMax,
      long requestMaxBytes
  ) {
  }

  public record PracticeMessageLimits(long messageMaxBytes, long requestMaxBytes) {
  }
}
