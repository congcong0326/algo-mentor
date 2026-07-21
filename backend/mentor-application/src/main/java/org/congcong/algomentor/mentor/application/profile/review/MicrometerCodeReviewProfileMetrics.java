package org.congcong.algomentor.mentor.application.profile.review;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

/** Micrometer 适配，只使用固定的 outcome 标签。 */
public final class MicrometerCodeReviewProfileMetrics implements CodeReviewProfileMetrics {

  private static final String METRIC_PREFIX = "learner.profile.review_consumer";

  private final MeterRegistry registry;

  public MicrometerCodeReviewProfileMetrics(MeterRegistry registry) {
    this.registry = Objects.requireNonNull(registry, "registry must not be null");
  }

  @Override
  public void recordResult(CodeReviewProfileUpdateResult result, Duration duration) {
    if (result == null) {
      return;
    }
    Counter.builder(METRIC_PREFIX)
        .tag("outcome", result.status().name().toLowerCase(Locale.ROOT))
        .register(registry)
        .increment();
    DistributionSummary.builder(METRIC_PREFIX + ".window_problems")
        .register(registry)
        .record(result.windowProblemCount());
    if (result.appliedCount() > 0) {
      Counter.builder(METRIC_PREFIX + ".version_updates")
          .register(registry)
          .increment(result.appliedCount());
    }
    recordDuration(duration);
  }

  @Override
  public void recordCallbackFailure(Duration duration) {
    Counter.builder(METRIC_PREFIX)
        .tag("outcome", CodeReviewProfileUpdateResult.Status.FAILED.name().toLowerCase(Locale.ROOT))
        .register(registry)
        .increment();
    recordDuration(duration);
  }

  @Override
  public void recordInvalidOutput() {
    Counter.builder(METRIC_PREFIX + ".invalid_output").register(registry).increment();
  }

  @Override
  public void recordStaleRetry() {
    Counter.builder(METRIC_PREFIX + ".stale_retry").register(registry).increment();
  }

  private void recordDuration(Duration duration) {
    if (duration != null && !duration.isNegative()) {
      Timer.builder(METRIC_PREFIX + ".duration").register(registry).record(duration);
    }
  }
}
