package org.congcong.algomentor.mentor.application.learningplan.proposal;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.springframework.transaction.annotation.Transactional;

public class LearningPlanProposalGroupService {

  private final LearningPlanProposalRepository repository;
  private final Clock clock;

  public LearningPlanProposalGroupService(LearningPlanProposalRepository repository, Clock clock) {
    this.repository = Objects.requireNonNull(repository, "repository");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  public LearningPlanProposalGroup createGroup(
      long userId,
      LearningPlanProposalType proposalType,
      LearningPlanProposalTargetType targetType,
      long targetId,
      String instruction) {
    Instant now = clock.instant();
    LearningPlanProposalGroup group = new LearningPlanProposalGroup(
        null,
        userId,
        proposalType,
        targetType,
        targetId,
        LearningPlanProposalGroupStatus.ACTIVE,
        instruction,
        null,
        now,
        now);
    return repository.saveGroup(group);
  }

  @Transactional
  public LearningPlanProposalGroup discardExtensionProposal(long userId, long planId, long proposalGroupId) {
    LearningPlanProposalGroup group = repository.findGroupForUserForUpdate(proposalGroupId, userId)
        .orElseThrow(() -> new LearningPlanException(
            "LEARNING_PLAN_PROPOSAL_GROUP_NOT_FOUND",
            "学习计划提案组不存在。"));
    if (group.proposalType() != LearningPlanProposalType.PLAN_EXTENSION
        || group.targetType() != LearningPlanProposalTargetType.PLAN
        || group.targetId() != planId) {
      throw new LearningPlanException("LEARNING_PLAN_PROPOSAL_GROUP_INVALID", "学习计划扩展提案组与请求不匹配。");
    }
    if (group.status() != LearningPlanProposalGroupStatus.ACTIVE) {
      throw new LearningPlanException("LEARNING_PLAN_PROPOSAL_GROUP_NOT_ACTIVE", "学习计划扩展提案组已不处于可丢弃状态。");
    }
    LearningPlanProposalGroup discarded = repository.discardActiveExtensionProposalGroup(
        userId,
        planId,
        proposalGroupId,
        clock.instant());
    if (discarded == null) {
      throw new LearningPlanException("LEARNING_PLAN_PROPOSAL_GROUP_NOT_ACTIVE", "学习计划扩展提案组已不处于可丢弃状态。");
    }
    return discarded;
  }
}
