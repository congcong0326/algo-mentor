package org.congcong.algomentor.mentor.application.learningplan.policy;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Locale;
import java.util.Objects;

/** Micrometer 访问检查指标实现，标签不包含用户或业务对象标识。 */
public final class MicrometerLearningPlanAiRevisionAccessMetrics
    implements LearningPlanAiRevisionAccessMetrics {
  public static final String METRIC_NAME = "algo.mentor.learning_plan.ai_revision.access_checks";

  private final MeterRegistry registry;

  public MicrometerLearningPlanAiRevisionAccessMetrics(MeterRegistry registry) {
    this.registry = Objects.requireNonNull(registry, "registry must not be null");
  }

  @Override
  public void record(LearningPlanAiRevisionAction action, Outcome outcome) {
    Counter.builder(METRIC_NAME)
        .tag("action", label(action))
        .tag("outcome", label(outcome))
        .register(registry)
        .increment();
  }

  private String label(Enum<?> value) {
    return Objects.requireNonNull(value, "metric label must not be null").name().toLowerCase(Locale.ROOT);
  }
}
