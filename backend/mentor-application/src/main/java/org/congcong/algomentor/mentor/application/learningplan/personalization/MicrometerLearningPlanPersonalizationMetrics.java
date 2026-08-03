package org.congcong.algomentor.mentor.application.learningplan.personalization;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

/** Micrometer 个性化指标实现；所有标签来自固定枚举或布尔值。 */
public final class MicrometerLearningPlanPersonalizationMetrics
    implements LearningPlanPersonalizationMetrics {

  private static final String CONTEXT_BUILD_TOTAL = "learning_plan_personalization_context_build_total";
  private static final String CONTEXT_BUILD_DURATION = "learning_plan_personalization_context_build_duration";
  private static final String SOURCE_LOAD_TOTAL = "learning_plan_personalization_source_load_total";
  private static final String ENTRY_COUNT = "learning_plan_personalization_entry_count";
  private static final String TOKEN_ESTIMATE = "learning_plan_personalization_token_estimate";

  private final MeterRegistry registry;

  public MicrometerLearningPlanPersonalizationMetrics(MeterRegistry registry) {
    this.registry = Objects.requireNonNull(registry, "registry must not be null");
  }

  @Override
  public void recordContextBuild(
      LearningPlanPersonalizationScenario scenario,
      boolean enabled,
      LearningPlanPersonalizationBuildOutcome outcome,
      boolean trimmed,
      Duration duration
  ) {
    String[] tags = contextTags(scenario, enabled, outcome, trimmed);
    Counter.builder(CONTEXT_BUILD_TOTAL).tags(tags).register(registry).increment();
    if (duration != null && !duration.isNegative()) {
      Timer.builder(CONTEXT_BUILD_DURATION).tags(tags).register(registry).record(duration);
    }
  }

  @Override
  public void recordSourceLoad(
      LearningPlanPersonalizationSource source,
      LearningPlanPersonalizationSourceOutcome outcome
  ) {
    Counter.builder(SOURCE_LOAD_TOTAL)
        .tags("source", label(source), "outcome", label(outcome))
        .register(registry)
        .increment();
  }

  @Override
  public void recordEntryCount(LearningPlanPersonalizationScenario scenario, int entryCount) {
    recordDistribution(ENTRY_COUNT, scenario, entryCount);
  }

  @Override
  public void recordTokenEstimate(LearningPlanPersonalizationScenario scenario, int tokenEstimate) {
    recordDistribution(TOKEN_ESTIMATE, scenario, tokenEstimate);
  }

  private void recordDistribution(String metricName, LearningPlanPersonalizationScenario scenario, int value) {
    if (value < 0) {
      return;
    }
    DistributionSummary.builder(metricName)
        .tag("scenario", label(scenario))
        .register(registry)
        .record(value);
  }

  private String[] contextTags(
      LearningPlanPersonalizationScenario scenario,
      boolean enabled,
      LearningPlanPersonalizationBuildOutcome outcome,
      boolean trimmed
  ) {
    return new String[] {
        "scenario", label(scenario),
        "enabled", Boolean.toString(enabled),
        "outcome", label(outcome),
        "trimmed", Boolean.toString(trimmed)
    };
  }

  private String label(Enum<?> value) {
    return Objects.requireNonNull(value, "metric label must not be null").name().toLowerCase(Locale.ROOT);
  }
}
