package org.congcong.algomentor.mentor.application.learningplan.proposal;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

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
}
