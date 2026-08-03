package org.congcong.algomentor.mentor.application.learningplan.proposal.stream;

import org.congcong.algomentor.mentor.application.learningplan.LearningPlanBrief;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftPlan;

/** 已校验的草案修订模型输出，包含本次解析后的 Brief 与计划内容。 */
public record LearningPlanDraftRevisionOutput(
    LearningPlanBrief resolvedBrief,
    LearningPlanDraftPlan generatedPlan
) {
}
