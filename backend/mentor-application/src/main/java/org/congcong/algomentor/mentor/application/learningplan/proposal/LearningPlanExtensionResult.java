package org.congcong.algomentor.mentor.application.learningplan.proposal;

import java.util.List;

public record LearningPlanExtensionResult(
    long proposalGroupId,
    long proposalId,
    long planId,
    int revisionNo,
    LearningPlanProposalRevisionStatus status,
    List<Long> supersededProposalIds,
    String summary,
    LearningPlanExtensionDraft extensionDraft
) {

  public LearningPlanExtensionResult {
    if (proposalGroupId < 1) {
      throw new IllegalArgumentException("Learning plan proposal group id must be positive");
    }
    if (proposalId < 1) {
      throw new IllegalArgumentException("Learning plan proposal id must be positive");
    }
    if (planId < 1) {
      throw new IllegalArgumentException("Learning plan id must be positive");
    }
    if (revisionNo < 1) {
      throw new IllegalArgumentException("Learning plan extension revision number must be positive");
    }
    if (status == null) {
      throw new IllegalArgumentException("Learning plan proposal revision status must not be null");
    }
    supersededProposalIds = supersededProposalIds == null ? List.of() : List.copyOf(supersededProposalIds);
    summary = summary == null ? "" : summary.trim();
  }

  public static LearningPlanExtensionResult fromRevision(
      LearningPlanExtensionRevision revision,
      List<Long> supersededProposalIds
  ) {
    LearningPlanExtensionDraft extension = revision.proposedExtension();
    return new LearningPlanExtensionResult(
        revision.proposalGroupId(),
        revision.id(),
        revision.planId(),
        revision.revisionNo(),
        revision.status(),
        supersededProposalIds,
        extension == null ? "" : extension.summary(),
        extension);
  }
}
