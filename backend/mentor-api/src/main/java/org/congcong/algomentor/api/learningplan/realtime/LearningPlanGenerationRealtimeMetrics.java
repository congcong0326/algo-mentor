package org.congcong.algomentor.api.learningplan.realtime;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/** 首次草案 Redis Stream 低基数可观测指标。 */
public final class LearningPlanGenerationRealtimeMetrics {

  private static final String METRIC_NAME = "learning_plan_generation_realtime_operations_total";
  private final MeterRegistry registry;

  public LearningPlanGenerationRealtimeMetrics(MeterRegistry registry) {
    this.registry = registry;
  }

  public void record(String operation, String outcome) {
    if (registry != null) {
      Counter.builder(METRIC_NAME)
          .tag("operation", operation)
          .tag("outcome", outcome)
          .register(registry)
          .increment();
    }
  }
}
