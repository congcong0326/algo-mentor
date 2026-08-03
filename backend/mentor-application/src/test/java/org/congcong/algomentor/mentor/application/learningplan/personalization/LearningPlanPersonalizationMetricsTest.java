package org.congcong.algomentor.mentor.application.learningplan.personalization;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimRevision;
import org.junit.jupiter.api.Test;

class LearningPlanPersonalizationMetricsTest {

  @Test
  void micrometerMetricsUseOnlyFixedScenarioSourceAndOutcomeLabels() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    LearningPlanPersonalizationMetrics metrics = new MicrometerLearningPlanPersonalizationMetrics(registry);

    metrics.recordContextBuild(
        LearningPlanPersonalizationScenario.DRAFT,
        true,
        LearningPlanPersonalizationBuildOutcome.SUCCESS,
        false,
        Duration.ofMillis(12));
    metrics.recordSourceLoad(
        LearningPlanPersonalizationSource.ACTIVE_CLAIMS,
        LearningPlanPersonalizationSourceOutcome.SUCCESS);
    metrics.recordEntryCount(LearningPlanPersonalizationScenario.DRAFT, 3);
    metrics.recordTokenEstimate(LearningPlanPersonalizationScenario.DRAFT, 42);

    assertThat(registry.get("learning_plan_personalization_context_build_total")
        .tags("scenario", "draft", "enabled", "true", "outcome", "success", "trimmed", "false")
        .counter()
        .count()).isEqualTo(1D);
    assertThat(registry.get("learning_plan_personalization_context_build_duration")
        .tags("scenario", "draft", "enabled", "true", "outcome", "success", "trimmed", "false")
        .timer()
        .count()).isEqualTo(1L);
    assertThat(registry.get("learning_plan_personalization_source_load_total")
        .tags("source", "active_claims", "outcome", "success")
        .counter()
        .count()).isEqualTo(1D);
    assertThat(registry.get("learning_plan_personalization_entry_count")
        .tag("scenario", "draft")
        .summary()
        .totalAmount()).isEqualTo(3D);
    assertThat(registry.get("learning_plan_personalization_token_estimate")
        .tag("scenario", "draft")
        .summary()
        .totalAmount()).isEqualTo(42D);
    assertThat(registry.getMeters()).allSatisfy(meter -> assertThat(meter.getId().getTags())
        .allSatisfy(tag -> {
          assertThat(tag.getKey()).isIn("scenario", "enabled", "outcome", "trimmed", "source");
          assertThat(tag.getValue()).doesNotContain("user-7", "claim-body", "review-body", "plan-9");
        }));
  }

  @Test
  void contextServiceRecordsDisabledAndPartialFailureOutcomesWithoutSensitiveValues() {
    CapturingMetrics metrics = new CapturingMetrics();
    LearningPlanPersonalizationContextService service = new LearningPlanPersonalizationContextService(
        new FailingClaimsProvider(),
        null,
        Clock.fixed(Instant.parse("2026-08-03T06:00:00Z"), ZoneOffset.UTC),
        metrics);

    LearningPlanPersonalizationSnapshot failed = service.snapshot(
        7L,
        true,
        LearningPlanPersonalizationScenario.REVISION);
    LearningPlanPersonalizationSnapshot disabled = service.snapshot(
        0L,
        false,
        LearningPlanPersonalizationScenario.EXTENSION);

    assertThat(failed.sourceOutcomes())
        .containsEntry(LearningPlanPersonalizationSource.ACTIVE_CLAIMS, LearningPlanPersonalizationSourceOutcome.ERROR);
    assertThat(disabled.enabled()).isFalse();
    assertThat(metrics.builds).containsExactly(
        new ContextBuild(
            LearningPlanPersonalizationScenario.REVISION,
            true,
            LearningPlanPersonalizationBuildOutcome.PARTIAL_FAILURE,
            false),
        new ContextBuild(
            LearningPlanPersonalizationScenario.EXTENSION,
            false,
            LearningPlanPersonalizationBuildOutcome.DISABLED,
            false));
    assertThat(metrics.sourceLoads).contains(
        new SourceLoad(LearningPlanPersonalizationSource.ACTIVE_CLAIMS, LearningPlanPersonalizationSourceOutcome.ERROR),
        new SourceLoad(LearningPlanPersonalizationSource.REVIEW_LOAD, LearningPlanPersonalizationSourceOutcome.DISABLED));
    assertThat(metrics.entryCounts).containsExactly(0, 0);
    assertThat(metrics.tokenEstimates).containsExactly(0, 0);
  }

  private record ContextBuild(
      LearningPlanPersonalizationScenario scenario,
      boolean enabled,
      LearningPlanPersonalizationBuildOutcome outcome,
      boolean trimmed
  ) {
  }

  private record SourceLoad(
      LearningPlanPersonalizationSource source,
      LearningPlanPersonalizationSourceOutcome outcome
  ) {
  }

  private static final class CapturingMetrics implements LearningPlanPersonalizationMetrics {
    private final List<ContextBuild> builds = new ArrayList<>();
    private final List<SourceLoad> sourceLoads = new ArrayList<>();
    private final List<Integer> entryCounts = new ArrayList<>();
    private final List<Integer> tokenEstimates = new ArrayList<>();

    @Override
    public void recordContextBuild(
        LearningPlanPersonalizationScenario scenario,
        boolean enabled,
        LearningPlanPersonalizationBuildOutcome outcome,
        boolean trimmed,
        Duration duration
    ) {
      builds.add(new ContextBuild(scenario, enabled, outcome, trimmed));
    }

    @Override
    public void recordSourceLoad(
        LearningPlanPersonalizationSource source,
        LearningPlanPersonalizationSourceOutcome outcome
    ) {
      sourceLoads.add(new SourceLoad(source, outcome));
    }

    @Override
    public void recordEntryCount(LearningPlanPersonalizationScenario scenario, int entryCount) {
      entryCounts.add(entryCount);
    }

    @Override
    public void recordTokenEstimate(LearningPlanPersonalizationScenario scenario, int tokenEstimate) {
      tokenEstimates.add(tokenEstimate);
    }
  }

  private static final class FailingClaimsProvider implements LearningPlanPersonalizationDataProvider {

    @Override
    public List<LearnerMemoryClaimRevision> findActiveClaims(long userId) {
      throw new IllegalStateException("internal claim source failure");
    }

    @Override
    public List<LearningPlanAbilityTagSummary> findAbilityTagSummaries(long userId) {
      return List.of();
    }

    @Override
    public Optional<LearningPlanActiveProgressSummary> findActivePlanProgress(long userId) {
      return Optional.empty();
    }

    @Override
    public Optional<LearningPlanReviewLoadSummary> findReviewLoad(long userId) {
      return Optional.empty();
    }
  }
}
