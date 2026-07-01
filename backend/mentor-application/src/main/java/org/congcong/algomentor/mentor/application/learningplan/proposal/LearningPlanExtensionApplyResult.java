package org.congcong.algomentor.mentor.application.learningplan.proposal;

public record LearningPlanExtensionApplyResult(
    long planId,
    long proposalGroupId,
    long proposalId,
    LearningPlanProposalGroupStatus status,
    int appendedPhaseCount
) {
}
