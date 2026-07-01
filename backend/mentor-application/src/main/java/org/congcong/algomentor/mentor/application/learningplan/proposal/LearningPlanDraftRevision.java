package org.congcong.algomentor.mentor.application.learningplan.proposal;

import java.time.Instant;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftPlan;

public record LearningPlanDraftRevision(
    Long id,
    long proposalGroupId,
    long draftId,
    long userId,
    int revisionNo,
    LearningPlanProposalRevisionStatus status,
    String instruction,
    LearningPlanDraftPlan basePlan,
    LearningPlanDraftPlan proposedPlan,
    String errorCode,
    String errorMessage,
    Instant createdAt,
    Instant updatedAt
) {

  public LearningPlanDraftRevision {
    if (id != null && id < 1) {
      throw new IllegalArgumentException("Learning plan draft revision id must be positive");
    }
    if (proposalGroupId < 1) {
      throw new IllegalArgumentException("Learning plan proposal group id must be positive");
    }
    if (draftId < 1) {
      throw new IllegalArgumentException("Learning plan draft id must be positive");
    }
    if (userId < 1) {
      throw new IllegalArgumentException("Learning plan draft revision user id must be positive");
    }
    if (revisionNo < 1) {
      throw new IllegalArgumentException("Learning plan draft revision number must be positive");
    }
    if (status == null) {
      throw new IllegalArgumentException("Learning plan draft revision status must not be null");
    }
    if (instruction == null || instruction.isBlank()) {
      throw new IllegalArgumentException("Learning plan draft revision instruction must not be blank");
    }
    if (status == LearningPlanProposalRevisionStatus.READY && proposedPlan == null) {
      throw new IllegalArgumentException("Learning plan draft revision proposed plan must not be null when ready");
    }
    if (status == LearningPlanProposalRevisionStatus.FAILED
        && (errorCode == null || errorCode.isBlank() || errorMessage == null || errorMessage.isBlank())) {
      throw new IllegalArgumentException("Learning plan draft revision failure detail must not be blank when failed");
    }
    if (createdAt == null) {
      throw new IllegalArgumentException("Learning plan draft revision created time must not be null");
    }
    if (updatedAt == null) {
      throw new IllegalArgumentException("Learning plan draft revision updated time must not be null");
    }
    instruction = instruction.trim();
  }

  public LearningPlanDraftRevision withId(Long nextId) {
    return new LearningPlanDraftRevision(
        nextId,
        proposalGroupId,
        draftId,
        userId,
        revisionNo,
        status,
        instruction,
        basePlan,
        proposedPlan,
        errorCode,
        errorMessage,
        createdAt,
        updatedAt);
  }

  public LearningPlanDraftRevision withStatus(LearningPlanProposalRevisionStatus nextStatus, Instant updatedAt) {
    return new LearningPlanDraftRevision(
        id,
        proposalGroupId,
        draftId,
        userId,
        revisionNo,
        nextStatus,
        instruction,
        basePlan,
        proposedPlan,
        errorCode,
        errorMessage,
        createdAt,
        updatedAt);
  }

  public LearningPlanDraftRevision withReady(LearningPlanDraftPlan nextProposedPlan, Instant updatedAt) {
    return new LearningPlanDraftRevision(
        id,
        proposalGroupId,
        draftId,
        userId,
        revisionNo,
        LearningPlanProposalRevisionStatus.READY,
        instruction,
        basePlan,
        nextProposedPlan,
        null,
        null,
        createdAt,
        updatedAt);
  }

  public LearningPlanDraftRevision withFailure(String nextErrorCode, String nextErrorMessage, Instant updatedAt) {
    return new LearningPlanDraftRevision(
        id,
        proposalGroupId,
        draftId,
        userId,
        revisionNo,
        LearningPlanProposalRevisionStatus.FAILED,
        instruction,
        basePlan,
        proposedPlan,
        nextErrorCode,
        nextErrorMessage,
        createdAt,
        updatedAt);
  }
}
