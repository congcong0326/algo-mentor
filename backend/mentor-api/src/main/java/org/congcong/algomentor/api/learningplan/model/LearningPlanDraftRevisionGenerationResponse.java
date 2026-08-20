package org.congcong.algomentor.api.learningplan.model;

import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalRevisionStatus;

/** 草案修订生成成功受理后的最小控制面响应。 */
public record LearningPlanDraftRevisionGenerationResponse(
    long revisionId,
    long proposalGroupId,
    long draftId,
    int revisionNo,
    LearningPlanProposalRevisionStatus status,
    String eventsUrl,
    String initialAfter,
    int realtimeProtocolVersion
) {
}
