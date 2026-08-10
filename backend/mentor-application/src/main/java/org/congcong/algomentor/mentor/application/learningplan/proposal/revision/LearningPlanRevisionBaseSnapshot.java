package org.congcong.algomentor.mentor.application.learningplan.proposal.revision;

import java.util.Objects;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanBrief;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftPlan;

/** 一次修订运行中不可变的 Brief 与 canonical plan 基线。 */
public record LearningPlanRevisionBaseSnapshot(
    LearningPlanBrief baseBrief,
    LearningPlanDraftPlan basePlan
) {

  public LearningPlanRevisionBaseSnapshot {
    baseBrief = Objects.requireNonNull(baseBrief, "Learning plan revision base brief must not be null");
    basePlan = Objects.requireNonNull(basePlan, "Learning plan revision base plan must not be null");
  }
}
