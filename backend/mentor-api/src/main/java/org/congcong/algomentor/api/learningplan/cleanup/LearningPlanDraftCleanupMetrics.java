package org.congcong.algomentor.api.learningplan.cleanup;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import org.congcong.algomentor.mentor.application.learningplan.cleanup.LearningPlanDraftCleanupResult;

/** 学习计划草案清理的删除量、耗时和失败指标。 */
public class LearningPlanDraftCleanupMetrics {

  private final Counter deletedDrafts;
  private final Counter deletedDailyUsageRows;
  private final Counter failures;
  private final Timer duration;

  public LearningPlanDraftCleanupMetrics(MeterRegistry registry) {
    if (registry == null) {
      deletedDrafts = null;
      deletedDailyUsageRows = null;
      failures = null;
      duration = null;
      return;
    }
    deletedDrafts = registry.counter("learning_plan_draft_cleanup_deleted_total", "type", "draft");
    deletedDailyUsageRows = registry.counter(
        "learning_plan_draft_cleanup_deleted_total", "type", "daily_usage");
    failures = registry.counter("learning_plan_draft_cleanup_failure_total");
    duration = registry.timer("learning_plan_draft_cleanup_duration");
  }

  public void recordSuccess(LearningPlanDraftCleanupResult result) {
    if (deletedDrafts == null) {
      return;
    }
    deletedDrafts.increment(result.deletedDrafts());
    deletedDailyUsageRows.increment(result.deletedDailyUsageRows());
    duration.record(result.duration().toNanos(), TimeUnit.NANOSECONDS);
  }

  public void recordFailure() {
    if (failures != null) {
      failures.increment();
    }
  }
}
