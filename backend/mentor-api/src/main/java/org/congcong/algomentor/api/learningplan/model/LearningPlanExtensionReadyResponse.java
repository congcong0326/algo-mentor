package org.congcong.algomentor.api.learningplan.model;

import java.util.List;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanExtensionDraft;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalRevisionStatus;

public record LearningPlanExtensionReadyResponse(
    long proposalGroupId,
    long proposalId,
    long planId,
    int revisionNo,
    LearningPlanProposalRevisionStatus status,
    List<Long> supersededProposalIds,
    String summary,
    LearningPlanExtensionDraft extensionDraft
) {
}
