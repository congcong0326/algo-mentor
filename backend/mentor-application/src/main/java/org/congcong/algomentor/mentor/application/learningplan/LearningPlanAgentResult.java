package org.congcong.algomentor.mentor.application.learningplan;

import java.util.List;

record LearningPlanAgentResult(
    LearningPlanAgentAction action,
    String assistantMessage,
    List<String> missingFields,
    LearningPlanDraftPlan draftPlan
) {

  static LearningPlanAgentResult askClarification(String assistantMessage, List<String> missingFields) {
    return new LearningPlanAgentResult(
        LearningPlanAgentAction.ASK_CLARIFICATION,
        assistantMessage,
        missingFields,
        null);
  }

  static LearningPlanAgentResult generated(String assistantMessage, LearningPlanDraftPlan draftPlan) {
    return new LearningPlanAgentResult(
        LearningPlanAgentAction.GENERATE_DRAFT,
        assistantMessage,
        List.of(),
        draftPlan);
  }
}
