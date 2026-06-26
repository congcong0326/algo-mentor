package org.congcong.algomentor.mentor.application.practice;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Objects;

public class MicrometerPracticeCodeReviewMetrics implements PracticeCodeReviewMetrics {

  private final MeterRegistry registry;

  public MicrometerPracticeCodeReviewMetrics(MeterRegistry registry) {
    this.registry = Objects.requireNonNull(registry, "registry must not be null");
  }

  @Override
  public void recordCompletionGate(PracticeCompletionGate gate) {
    if (gate == null) {
      return;
    }
    Counter.builder("practice.completion_gate.evaluations")
        .tag("canComplete", Boolean.toString(gate.canComplete()))
        .tag("reason", gate.reasonCode().name())
        .register(registry)
        .increment();
  }

}
