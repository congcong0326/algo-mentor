package org.congcong.algomentor.mentor.application.learningplan.proposal;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanPhaseDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanRepository;
import org.congcong.algomentor.mentor.application.practice.PracticeProgress;
import org.congcong.algomentor.mentor.application.practice.PracticeSessionRepository;
import org.springframework.transaction.annotation.Transactional;

public class LearningPlanExtensionApplyService {

  private final LearningPlanProposalRepository proposalRepository;
  private final LearningPlanRepository learningPlanRepository;
  private final PracticeSessionRepository practiceSessionRepository;
  private final LearningPlanExtensionValidator validator;
  private final Clock clock;

  public LearningPlanExtensionApplyService(
      LearningPlanProposalRepository proposalRepository,
      LearningPlanRepository learningPlanRepository,
      PracticeSessionRepository practiceSessionRepository,
      LearningPlanExtensionValidator validator,
      Clock clock) {
    this.proposalRepository = Objects.requireNonNull(proposalRepository, "proposalRepository");
    this.learningPlanRepository = Objects.requireNonNull(learningPlanRepository, "learningPlanRepository");
    this.practiceSessionRepository = Objects.requireNonNull(practiceSessionRepository, "practiceSessionRepository");
    this.validator = Objects.requireNonNull(validator, "validator");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  @Transactional
  public LearningPlanExtensionApplyResult apply(long userId, long planId, long proposalGroupId) {
    LearningPlanProposalGroup group = proposalRepository.findGroupForUser(proposalGroupId, userId)
        .orElseThrow(() -> new LearningPlanException(
            "LEARNING_PLAN_PROPOSAL_GROUP_NOT_FOUND",
            "学习计划提案组不存在。"));
    validateGroup(group, planId);

    LearningPlanExtensionRevision revision = proposalRepository.findLatestReadyExtensionRevision(proposalGroupId)
        .orElseThrow(() -> new LearningPlanException(
            "LEARNING_PLAN_PROPOSAL_NOT_LATEST",
            "没有可应用的最新学习计划扩展提案。"));
    if (!Objects.equals(group.latestProposalId(), revision.id())) {
      throw new LearningPlanException(
          "LEARNING_PLAN_PROPOSAL_NOT_LATEST",
          "没有可应用的最新学习计划扩展提案。");
    }
    validateRevision(revision, userId, planId);

    LearningPlan currentPlan = learningPlanRepository.findPlanByIdForUser(planId, userId)
        .orElseThrow(() -> new LearningPlanException("LEARNING_PLAN_NOT_FOUND", "学习计划不存在。"));
    List<PracticeProgress> progress = practiceSessionRepository.findProgressByPlan(userId, planId);
    int currentMaxPhaseIndex = currentMaxPhaseIndex(currentPlan);
    LearningPlanExtensionDraft appendReadyExtension = appendReadyExtension(
        revision,
        revision.proposedExtension(),
        currentMaxPhaseIndex);

    try {
      validator.validate(appendReadyExtension, currentPlan, progress);
    } catch (LearningPlanException exception) {
      if ("LEARNING_PLAN_EXTENSION_INVALID".equals(exception.code())
          && hasCurrentPlanProblemSlug(appendReadyExtension, currentPlan)) {
        throw new LearningPlanException("LEARNING_PLAN_EXTENSION_CONFLICT", "扩展提案与当前学习计划冲突，请重新生成。");
      }
      throw exception;
    }

    learningPlanRepository.appendPhases(userId, planId, appendReadyExtension.newPhases());

    Instant now = clock.instant();
    LearningPlanExtensionRevision appliedRevision = revision
        .withReady(revision.previousExtension(), appendReadyExtension, now)
        .withApplied(now, now);
    proposalRepository.saveExtensionRevision(appliedRevision);
    proposalRepository.saveGroup(group.withStatus(LearningPlanProposalGroupStatus.APPLIED, now));

    return new LearningPlanExtensionApplyResult(
        planId,
        proposalGroupId,
        revision.id(),
        LearningPlanProposalGroupStatus.APPLIED,
        appendReadyExtension.newPhases().size());
  }

  private static void validateGroup(LearningPlanProposalGroup group, long planId) {
    if (group.status() != LearningPlanProposalGroupStatus.ACTIVE
        || group.proposalType() != LearningPlanProposalType.PLAN_EXTENSION
        || group.targetType() != LearningPlanProposalTargetType.PLAN
        || group.targetId() != planId) {
      throw new LearningPlanException("LEARNING_PLAN_PROPOSAL_GROUP_INVALID", "学习计划扩展提案组与请求不匹配。");
    }
  }

  private static void validateRevision(LearningPlanExtensionRevision revision, long userId, long planId) {
    if (revision.planId() != planId || revision.userId() != userId) {
      throw new LearningPlanException("LEARNING_PLAN_PROPOSAL_REVISION_INVALID", "学习计划扩展提案与请求不匹配。");
    }
  }

  private static LearningPlanExtensionDraft appendReadyExtension(
      LearningPlanExtensionRevision revision,
      LearningPlanExtensionDraft extension,
      int currentMaxPhaseIndex) {
    if (currentMaxPhaseIndex == revision.baseMaxPhaseIndex()) {
      return extension;
    }
    return reindexExtension(extension, currentMaxPhaseIndex);
  }

  private static LearningPlanExtensionDraft reindexExtension(
      LearningPlanExtensionDraft extension,
      int currentMaxPhaseIndex) {
    ArrayList<LearningPlanPhaseDraft> reindexedPhases = new ArrayList<>();
    int nextPhaseIndex = currentMaxPhaseIndex + 1;
    for (LearningPlanPhaseDraft phase : extension.newPhases()) {
      reindexedPhases.add(new LearningPlanPhaseDraft(
          nextPhaseIndex++,
          phase.title(),
          phase.durationWeeks(),
          phase.focus(),
          phase.objectives(),
          phase.recommendedTags(),
          phase.acceptanceCriteria(),
          phase.reviewAdvice(),
          phase.problems()));
    }
    return new LearningPlanExtensionDraft(extension.summary(), reindexedPhases, extension.metadata());
  }

  private static int currentMaxPhaseIndex(LearningPlan plan) {
    return plan.plan().phases().stream()
        .mapToInt(LearningPlanPhaseDraft::phaseIndex)
        .max()
        .orElse(0);
  }

  private static boolean hasCurrentPlanProblemSlug(LearningPlanExtensionDraft extension, LearningPlan currentPlan) {
    Set<String> currentSlugs = new HashSet<>();
    for (LearningPlanPhaseDraft phase : currentPlan.plan().phases()) {
      phase.problems().forEach(problem -> currentSlugs.add(problem.slug()));
    }
    return extension.newPhases().stream()
        .flatMap(phase -> phase.problems().stream())
        .anyMatch(problem -> currentSlugs.contains(problem.slug()));
  }
}
