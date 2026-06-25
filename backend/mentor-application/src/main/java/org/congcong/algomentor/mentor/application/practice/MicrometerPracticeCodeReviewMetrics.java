package org.congcong.algomentor.mentor.application.practice;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Objects;

public class MicrometerPracticeCodeReviewMetrics implements PracticeCodeReviewMetrics {

  private final MeterRegistry registry;

  public MicrometerPracticeCodeReviewMetrics(MeterRegistry registry) {
    this.registry = Objects.requireNonNull(registry, "registry must not be null");
  }

  @Override
  public void recordCapability(
      boolean codeSubmissionCandidate,
      PracticeReviewStatus status,
      String failureCode,
      Duration duration
  ) {
    String candidate = Boolean.toString(codeSubmissionCandidate);
    String statusTag = safeStatus(status);
    Counter.builder("practice.code_review.capability.calls")
        .tag("candidate", candidate)
        .tag("status", statusTag)
        .tag("failureCode", safeFailureCode(failureCode))
        .register(registry)
        .increment();
    Timer.builder("practice.code_review.capability.duration")
        .tag("candidate", candidate)
        .tag("status", statusTag)
        .register(registry)
        .record(duration == null ? Duration.ZERO : duration);
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

  private String safeStatus(PracticeReviewStatus status) {
    return status == null ? "UNKNOWN" : status.name();
  }

  private String safeFailureCode(String failureCode) {
    return failureCode == null || failureCode.isBlank() ? "none" : failureCode.trim();
  }
}
