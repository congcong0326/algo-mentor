package org.congcong.algomentor.api.learningplan.recovery;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.congcong.algomentor.api.learningplan.config.LearningPlanGovernanceProperties;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanDraftRevision;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalRepository;
import org.congcong.algomentor.mentor.application.learningplan.proposal.stream.LearningPlanDraftRevisionGenerationConstants;
import org.congcong.algomentor.mentor.application.learningplan.proposal.stream.LearningPlanDraftRevisionGenerationEvent;
import org.congcong.algomentor.mentor.application.learningplan.proposal.stream.LearningPlanDraftRevisionGenerationEventPublisher;
import org.congcong.algomentor.mentor.application.learningplan.proposal.stream.LearningPlanDraftRevisionGenerationMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/** 启动时把无法续跑的 revision 生成条件收敛为安全失败，不重建 Agent。 */
public final class LearningPlanDraftRevisionGenerationStartupRecovery implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(LearningPlanDraftRevisionGenerationStartupRecovery.class);
  private final LearningPlanProposalRepository repository;
  private final LearningPlanDraftRevisionGenerationEventPublisher eventPublisher;
  private final Clock clock;
  private final LearningPlanGovernanceProperties.GenerationRecovery properties;
  private final LearningPlanDraftRevisionGenerationMetrics metrics;

  public LearningPlanDraftRevisionGenerationStartupRecovery(
      LearningPlanProposalRepository repository,
      LearningPlanDraftRevisionGenerationEventPublisher eventPublisher,
      Clock clock,
      LearningPlanGovernanceProperties.GenerationRecovery properties
  ) {
    this(repository, eventPublisher, clock, properties, LearningPlanDraftRevisionGenerationMetrics.NOOP);
  }

  public LearningPlanDraftRevisionGenerationStartupRecovery(
      LearningPlanProposalRepository repository,
      LearningPlanDraftRevisionGenerationEventPublisher eventPublisher,
      Clock clock,
      LearningPlanGovernanceProperties.GenerationRecovery properties,
      LearningPlanDraftRevisionGenerationMetrics metrics
  ) {
    this.repository = Objects.requireNonNull(repository, "repository");
    this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.properties = Objects.requireNonNull(properties, "properties");
    this.metrics = Objects.requireNonNull(metrics, "metrics");
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!properties.isEnabled()) {
      return;
    }
    Instant completedAt = clock.instant();
    List<LearningPlanDraftRevision> candidates = repository.findInterruptedDraftRevisionGenerations(
        completedAt.minus(properties.getMinimumAge()));
    int recovered = 0;
    for (LearningPlanDraftRevision revision : candidates) {
      if (repository.failDraftRevisionIfGenerating(
          revision.id(), revision.userId(), revision.draftId(),
          LearningPlanDraftRevisionGenerationConstants.GENERATION_INTERRUPTED_CODE,
          LearningPlanDraftRevisionGenerationConstants.GENERATION_INTERRUPTED_MESSAGE,
          completedAt).isPresent()) {
        recovered++;
        metrics.recordFailed(revision.generationStartedAt(), completedAt);
        try {
          eventPublisher.append(revision.draftId(), revision.id(),
              new LearningPlanDraftRevisionGenerationEvent.Failed(
                  LearningPlanDraftRevisionGenerationConstants.GENERATION_INTERRUPTED_CODE));
        } catch (RuntimeException exception) {
          log.warn("Learning plan revision recovery realtime append failed. revisionId={} exceptionType={}",
              revision.id(), exception.getClass().getSimpleName());
        }
      }
    }
    if (recovered > 0) {
      log.warn("Marked interrupted learning plan draft revisions as failed at startup. recoveredRevisionCount={}", recovered);
    }
  }
}
