package org.congcong.algomentor.api.learningplan.model;

import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanExtensionApplyResult;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalGroupStatus;

public record LearningPlanExtensionApplyResponse(
    long planId,
    long proposalGroupId,
    long proposalId,
    LearningPlanProposalGroupStatus status,
    int appendedPhaseCount
) {

  public static LearningPlanExtensionApplyResponse fromResult(LearningPlanExtensionApplyResult result) {
    return new LearningPlanExtensionApplyResponse(
        result.planId(),
        result.proposalGroupId(),
        result.proposalId(),
        result.status(),
        result.appendedPhaseCount());
  }
}
