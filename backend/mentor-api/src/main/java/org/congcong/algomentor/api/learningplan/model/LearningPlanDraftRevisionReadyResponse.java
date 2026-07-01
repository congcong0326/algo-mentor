package org.congcong.algomentor.api.learningplan.model;

import java.util.List;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalRevisionStatus;

public record LearningPlanDraftRevisionReadyResponse(
    long proposalGroupId,
    long proposalId,
    long draftId,
    int revisionNo,
    LearningPlanProposalRevisionStatus status,
    List<Long> supersededProposalIds,
    LearningPlanDraftResponse draft
) {
}
