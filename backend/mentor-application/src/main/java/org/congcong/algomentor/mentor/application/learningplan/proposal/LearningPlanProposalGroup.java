package org.congcong.algomentor.mentor.application.learningplan.proposal;

import java.time.Instant;

public record LearningPlanProposalGroup(
    Long id,
    long userId,
    LearningPlanProposalType proposalType,
    LearningPlanProposalTargetType targetType,
    long targetId,
    LearningPlanProposalGroupStatus status,
    String initialInstruction,
    Long latestProposalId,
    Instant createdAt,
    Instant updatedAt
) {

  public LearningPlanProposalGroup {
    if (id != null && id < 1) {
      throw new IllegalArgumentException("Learning plan proposal group id must be positive");
    }
    if (userId < 1) {
      throw new IllegalArgumentException("Learning plan proposal group user id must be positive");
    }
    if (proposalType == null) {
      throw new IllegalArgumentException("Learning plan proposal type must not be null");
    }
    if (targetType == null) {
      throw new IllegalArgumentException("Learning plan proposal target type must not be null");
    }
    if ((proposalType == LearningPlanProposalType.DRAFT_REVISION
        && targetType != LearningPlanProposalTargetType.DRAFT)
        || (proposalType == LearningPlanProposalType.PLAN_EXTENSION
        && targetType != LearningPlanProposalTargetType.PLAN)) {
      throw new IllegalArgumentException("Learning plan proposal type and target type are incompatible");
    }
    if (targetId < 1) {
      throw new IllegalArgumentException("Learning plan proposal target id must be positive");
    }
    if (status == null) {
      throw new IllegalArgumentException("Learning plan proposal group status must not be null");
    }
    if (initialInstruction == null || initialInstruction.isBlank()) {
      throw new IllegalArgumentException("Learning plan proposal initial instruction must not be blank");
    }
    if (latestProposalId != null && latestProposalId < 1) {
      throw new IllegalArgumentException("Learning plan latest proposal id must be positive");
    }
    if (createdAt == null) {
      throw new IllegalArgumentException("Learning plan proposal group created time must not be null");
    }
    if (updatedAt == null) {
      throw new IllegalArgumentException("Learning plan proposal group updated time must not be null");
    }
    initialInstruction = initialInstruction.trim();
  }

  public LearningPlanProposalGroup withId(Long nextId) {
    return new LearningPlanProposalGroup(
        nextId,
        userId,
        proposalType,
        targetType,
        targetId,
        status,
        initialInstruction,
        latestProposalId,
        createdAt,
        updatedAt);
  }

  public LearningPlanProposalGroup withLatestProposalId(Long nextLatestProposalId, Instant updatedAt) {
    return new LearningPlanProposalGroup(
        id,
        userId,
        proposalType,
        targetType,
        targetId,
        status,
        initialInstruction,
        nextLatestProposalId,
        createdAt,
        updatedAt);
  }

  public LearningPlanProposalGroup withStatus(LearningPlanProposalGroupStatus nextStatus, Instant updatedAt) {
    return new LearningPlanProposalGroup(
        id,
        userId,
        proposalType,
        targetType,
        targetId,
        nextStatus,
        initialInstruction,
        latestProposalId,
        createdAt,
        updatedAt);
  }
}
