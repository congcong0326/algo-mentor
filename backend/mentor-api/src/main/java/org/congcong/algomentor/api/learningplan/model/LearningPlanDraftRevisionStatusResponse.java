package org.congcong.algomentor.api.learningplan.model;

import java.time.Instant;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanDraftRevision;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalRevisionStatus;

/** 不包含 instruction 或快照的草案修订权威状态响应。 */
public record LearningPlanDraftRevisionStatusResponse(
    long revisionId,
    long proposalGroupId,
    long draftId,
    int revisionNo,
    LearningPlanProposalRevisionStatus status,
    String errorCode,
    String errorMessage,
    Instant startedAt,
    Instant completedAt
) {
  public static LearningPlanDraftRevisionStatusResponse fromRevision(LearningPlanDraftRevision revision) {
    boolean terminalError = revision.status() == LearningPlanProposalRevisionStatus.FAILED
        || revision.status() == LearningPlanProposalRevisionStatus.SUPERSEDED;
    return new LearningPlanDraftRevisionStatusResponse(
        revision.id(), revision.proposalGroupId(), revision.draftId(), revision.revisionNo(), revision.status(),
        terminalError ? revision.errorCode() : null, terminalError ? revision.errorMessage() : null,
        revision.generationStartedAt(), revision.generationCompletedAt());
  }
}
