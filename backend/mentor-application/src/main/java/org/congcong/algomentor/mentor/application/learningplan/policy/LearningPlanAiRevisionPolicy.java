package org.congcong.algomentor.mentor.application.learningplan.policy;

/** 学习计划 AI 修订灰度策略，三个字段必须完整配置。 */
public record LearningPlanAiRevisionPolicy(
    boolean templateDraftRevisionEnabled,
    boolean savedPlanRevisionEnabled,
    boolean personalizedDraftRevisionEnabled
) {
  public LearningPlanAiRevisionCapabilities capabilities() {
    return new LearningPlanAiRevisionCapabilities(
        templateDraftRevisionEnabled,
        savedPlanRevisionEnabled,
        personalizedDraftRevisionEnabled);
  }

  public static LearningPlanAiRevisionPolicy defaults() {
    return new LearningPlanAiRevisionPolicy(false, false, true);
  }
}
