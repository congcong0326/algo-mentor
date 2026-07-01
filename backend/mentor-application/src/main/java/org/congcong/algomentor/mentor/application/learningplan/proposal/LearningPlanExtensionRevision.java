package org.congcong.algomentor.mentor.application.learningplan.proposal;

import java.time.Instant;
import java.util.Map;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftPlan;

public record LearningPlanExtensionRevision(
    Long id,
    long proposalGroupId,
    long planId,
    long userId,
    int revisionNo,
    LearningPlanProposalRevisionStatus status,
    String instruction,
    LearningPlanDraftPlan basePlan,
    Map<String, Object> progressSnapshot,
    int baseMaxPhaseIndex,
    LearningPlanExtensionDraft previousExtension,
    LearningPlanExtensionDraft proposedExtension,
    Instant appliedAt,
    String errorCode,
    String errorMessage,
    Instant createdAt,
    Instant updatedAt
) {

  public LearningPlanExtensionRevision {
    if (id != null && id < 1) {
      throw new IllegalArgumentException("Learning plan extension revision id must be positive");
    }
    if (proposalGroupId < 1) {
      throw new IllegalArgumentException("Learning plan proposal group id must be positive");
    }
    if (planId < 1) {
      throw new IllegalArgumentException("Learning plan extension revision plan id must be positive");
    }
    if (userId < 1) {
      throw new IllegalArgumentException("Learning plan extension revision user id must be positive");
    }
    if (revisionNo < 1) {
      throw new IllegalArgumentException("Learning plan extension revision number must be positive");
    }
    if (status == null) {
      throw new IllegalArgumentException("Learning plan extension revision status must not be null");
    }
    if (instruction == null || instruction.isBlank()) {
      throw new IllegalArgumentException("Learning plan extension revision instruction must not be blank");
    }
    if (basePlan == null) {
      throw new IllegalArgumentException("Learning plan extension revision base plan must not be null");
    }
    if (status == LearningPlanProposalRevisionStatus.READY && proposedExtension == null) {
      throw new IllegalArgumentException("Learning plan extension revision proposed extension must not be null when ready");
    }
    if (status == LearningPlanProposalRevisionStatus.APPLIED && (proposedExtension == null || appliedAt == null)) {
      throw new IllegalArgumentException("Learning plan extension revision applied detail must not be null when applied");
    }
    if (status == LearningPlanProposalRevisionStatus.FAILED
        && (errorCode == null || errorCode.isBlank() || errorMessage == null || errorMessage.isBlank())) {
      throw new IllegalArgumentException("Learning plan extension revision failure detail must not be blank when failed");
    }
    if (createdAt == null) {
      throw new IllegalArgumentException("Learning plan extension revision created time must not be null");
    }
    if (updatedAt == null) {
      throw new IllegalArgumentException("Learning plan extension revision updated time must not be null");
    }
    instruction = instruction.trim();
    progressSnapshot = progressSnapshot == null ? Map.of() : Map.copyOf(progressSnapshot);
  }

  public LearningPlanExtensionRevision withId(Long nextId) {
    return new LearningPlanExtensionRevision(
        nextId,
        proposalGroupId,
        planId,
        userId,
        revisionNo,
        status,
        instruction,
        basePlan,
        progressSnapshot,
        baseMaxPhaseIndex,
        previousExtension,
        proposedExtension,
        appliedAt,
        errorCode,
        errorMessage,
        createdAt,
        updatedAt);
  }

  public LearningPlanExtensionRevision withStatus(LearningPlanProposalRevisionStatus nextStatus, Instant updatedAt) {
    return new LearningPlanExtensionRevision(
        id,
        proposalGroupId,
        planId,
        userId,
        revisionNo,
        nextStatus,
        instruction,
        basePlan,
        progressSnapshot,
        baseMaxPhaseIndex,
        previousExtension,
        proposedExtension,
        appliedAt,
        errorCode,
        errorMessage,
        createdAt,
        updatedAt);
  }

  public LearningPlanExtensionRevision withReady(
      LearningPlanExtensionDraft nextPreviousExtension,
      LearningPlanExtensionDraft nextProposedExtension,
      Instant updatedAt) {
    return new LearningPlanExtensionRevision(
        id,
        proposalGroupId,
        planId,
        userId,
        revisionNo,
        LearningPlanProposalRevisionStatus.READY,
        instruction,
        basePlan,
        progressSnapshot,
        baseMaxPhaseIndex,
        nextPreviousExtension,
        nextProposedExtension,
        appliedAt,
        null,
        null,
        createdAt,
        updatedAt);
  }

  public LearningPlanExtensionRevision withApplied(Instant appliedAt, Instant updatedAt) {
    return new LearningPlanExtensionRevision(
        id,
        proposalGroupId,
        planId,
        userId,
        revisionNo,
        LearningPlanProposalRevisionStatus.APPLIED,
        instruction,
        basePlan,
        progressSnapshot,
        baseMaxPhaseIndex,
        previousExtension,
        proposedExtension,
        appliedAt,
        errorCode,
        errorMessage,
        createdAt,
        updatedAt);
  }

  public LearningPlanExtensionRevision withFailure(String nextErrorCode, String nextErrorMessage, Instant updatedAt) {
    return new LearningPlanExtensionRevision(
        id,
        proposalGroupId,
        planId,
        userId,
        revisionNo,
        LearningPlanProposalRevisionStatus.FAILED,
        instruction,
        basePlan,
        progressSnapshot,
        baseMaxPhaseIndex,
        previousExtension,
        proposedExtension,
        appliedAt,
        nextErrorCode,
        nextErrorMessage,
        createdAt,
        updatedAt);
  }
}
