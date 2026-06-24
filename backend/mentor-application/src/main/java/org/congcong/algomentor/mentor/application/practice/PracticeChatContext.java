package org.congcong.algomentor.mentor.application.practice;

import java.util.Objects;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanPhaseDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemDraft;

public record PracticeChatContext(
    LearningPlan plan,
    LearningPlanPhaseDraft phase,
    LearningPlanProblemDraft planProblem,
    PracticeChatProblemDetail problemDetail,
    String locale
) {

  public PracticeChatContext {
    Objects.requireNonNull(plan, "Practice chat learning plan must not be null");
    Objects.requireNonNull(phase, "Practice chat learning plan phase must not be null");
    Objects.requireNonNull(planProblem, "Practice chat plan problem must not be null");
    locale = locale == null || locale.isBlank() ? "zh-CN" : locale;
  }
}
