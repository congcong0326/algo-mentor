package org.congcong.algomentor.mentor.application.learningplan.policy;

public record LearningPlanAiRevisionCapabilities(
    boolean templateDraftRevisionEnabled,
    boolean savedPlanRevisionEnabled,
    boolean personalizedDraftRevisionEnabled
) {
  public static LearningPlanAiRevisionCapabilities disabled() {
    return new LearningPlanAiRevisionCapabilities(false, false, false);
  }

  public boolean allows(LearningPlanAiRevisionAction action) {
    return switch (action) {
      case TEMPLATE_DRAFT_REVISION -> templateDraftRevisionEnabled;
      case SAVED_PLAN_REVISION -> savedPlanRevisionEnabled;
      case PERSONALIZED_DRAFT_REVISION -> personalizedDraftRevisionEnabled;
    };
  }
}
