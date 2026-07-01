package org.congcong.algomentor.mentor.application.learningplan.proposal;

import java.util.List;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftResult;

public record LearningPlanDraftRevisionResult(
    long proposalGroupId,
    long proposalId,
    long draftId,
    int revisionNo,
    LearningPlanProposalRevisionStatus status,
    List<Long> supersededProposalIds,
    LearningPlanDraftResult draft
) {

  public LearningPlanDraftRevisionResult {
    if (proposalGroupId < 1) {
      throw new IllegalArgumentException("Learning plan proposal group id must be positive");
    }
    if (proposalId < 1) {
      throw new IllegalArgumentException("Learning plan proposal id must be positive");
    }
    if (draftId < 1) {
      throw new IllegalArgumentException("Learning plan draft id must be positive");
    }
    if (revisionNo < 1) {
      throw new IllegalArgumentException("Learning plan draft revision number must be positive");
    }
    if (status == null) {
      throw new IllegalArgumentException("Learning plan proposal revision status must not be null");
    }
    supersededProposalIds = supersededProposalIds == null ? List.of() : List.copyOf(supersededProposalIds);
  }

  public static LearningPlanDraftRevisionResult fromRevision(
      LearningPlanDraftRevision revision,
      List<Long> supersededProposalIds,
      LearningPlanDraftResult draft
  ) {
    return new LearningPlanDraftRevisionResult(
        revision.proposalGroupId(),
        revision.id(),
        revision.draftId(),
        revision.revisionNo(),
        revision.status(),
        supersededProposalIds,
        draft);
  }
}
