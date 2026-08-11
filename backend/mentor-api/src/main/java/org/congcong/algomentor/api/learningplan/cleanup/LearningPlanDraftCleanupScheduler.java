package org.congcong.algomentor.api.learningplan.cleanup;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.congcong.algomentor.api.learningplan.config.LearningPlanGovernanceProperties;
import org.congcong.algomentor.mentor.application.learningplan.cleanup.LearningPlanDraftCleanupResult;
import org.congcong.algomentor.mentor.application.learningplan.cleanup.LearningPlanDraftCleanupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 使用独立单线程调度器周期清理学习计划过期数据。 */
public class LearningPlanDraftCleanupScheduler {

  private static final Logger log = LoggerFactory.getLogger(LearningPlanDraftCleanupScheduler.class);

  private final LearningPlanDraftCleanupService cleanupService;
  private final LearningPlanGovernanceProperties.Cleanup properties;
  private final LearningPlanDraftCleanupMetrics metrics;
  private ScheduledExecutorService executor;

  public LearningPlanDraftCleanupScheduler(
      LearningPlanDraftCleanupService cleanupService,
      LearningPlanGovernanceProperties.Cleanup properties,
      LearningPlanDraftCleanupMetrics metrics
  ) {
    this.cleanupService = cleanupService;
    this.properties = properties;
    this.metrics = metrics;
  }

  public synchronized void start() {
    if (!properties.isEnabled() || executor != null) {
      return;
    }
    executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
      Thread thread = new Thread(runnable, "learning-plan-draft-cleanup");
      thread.setDaemon(true);
      return thread;
    });
    long delayMillis = properties.getFixedDelay().toMillis();
    executor.scheduleWithFixedDelay(this::runSafely, delayMillis, delayMillis, TimeUnit.MILLISECONDS);
  }

  public synchronized void stop() {
    if (executor == null) {
      return;
    }
    executor.shutdownNow();
    executor = null;
  }

  void runSafely() {
    try {
      LearningPlanDraftCleanupResult result = cleanupService.cleanupOnce(
          properties.getBatchSize(),
          properties.getDailyUsageRetentionDays());
      metrics.recordSuccess(result);
      if (result.deletedDrafts() > 0 || result.deletedDailyUsageRows() > 0) {
        log.info(
            "Learning plan draft cleanup completed. deletedDrafts={} deletedDailyUsageRows={} durationMs={}",
            result.deletedDrafts(),
            result.deletedDailyUsageRows(),
            result.duration().toMillis());
      }
    } catch (RuntimeException exception) {
      metrics.recordFailure();
      log.warn("Learning plan draft cleanup failed and will retry later", exception);
    }
  }
}
