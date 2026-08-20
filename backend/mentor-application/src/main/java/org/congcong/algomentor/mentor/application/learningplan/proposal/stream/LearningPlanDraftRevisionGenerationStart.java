package org.congcong.algomentor.mentor.application.learningplan.proposal.stream;

import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanDraftRevision;

/** 已受理修订生成的控制面结果。 */
public record LearningPlanDraftRevisionGenerationStart(
    LearningPlanDraftRevision revision,
    boolean newlyStarted
) {
}
