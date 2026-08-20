package org.congcong.algomentor.api.learningplan.recovery;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.congcong.algomentor.api.learningplan.config.LearningPlanGovernanceProperties;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftRepository;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanDraftGenerationConstants;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanDraftGenerationEvent;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanDraftGenerationEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/**
 * 进程启动时一次性把无法续跑的首次草案生成收敛为稳定失败状态。
 *
 * <p>数据库提交成功后才尽力补写实时失败事件；Redis 不可用不能阻断启动或改变终态。</p>
 */
public final class LearningPlanDraftGenerationStartupRecovery implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(LearningPlanDraftGenerationStartupRecovery.class);

  private final LearningPlanDraftRepository draftRepository;
  private final LearningPlanDraftGenerationEventPublisher eventPublisher;
  private final Clock clock;
  private final LearningPlanGovernanceProperties.GenerationRecovery properties;

  public LearningPlanDraftGenerationStartupRecovery(
      LearningPlanDraftRepository draftRepository,
      LearningPlanDraftGenerationEventPublisher eventPublisher,
      Clock clock,
      LearningPlanGovernanceProperties.GenerationRecovery properties
  ) {
    this.draftRepository = Objects.requireNonNull(draftRepository, "draftRepository");
    this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.properties = Objects.requireNonNull(properties, "properties");
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!properties.isEnabled()) {
      return;
    }
    Instant completedAt = clock.instant();
    List<Long> recoveredDraftIds = draftRepository.failInterruptedGenerations(
        completedAt.minus(properties.getMinimumAge()),
        LearningPlanDraftGenerationConstants.GENERATION_INTERRUPTED_CODE,
        LearningPlanDraftGenerationConstants.GENERATION_INTERRUPTED_MESSAGE,
        completedAt);
    for (long draftId : recoveredDraftIds) {
      try {
        eventPublisher.append(draftId, new LearningPlanDraftGenerationEvent.Failed(
            LearningPlanDraftGenerationConstants.GENERATION_INTERRUPTED_CODE));
      } catch (RuntimeException exception) {
        log.warn("Learning plan generation recovery realtime append failed. draftId={} exceptionType={}",
            draftId, exception.getClass().getSimpleName());
      }
    }
    if (!recoveredDraftIds.isEmpty()) {
      log.warn("Marked interrupted learning plan draft generations as failed at application startup. recoveredDraftCount={}",
          recoveredDraftIds.size());
    }
  }
}
