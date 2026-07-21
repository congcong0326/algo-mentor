package org.congcong.algomentor.mentor.application.profile.review;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class MicrometerCodeReviewProfileMetricsTest {

  @Test
  void recordsOnlyFixedOutcomeTagsAndAggregateValues() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    MicrometerCodeReviewProfileMetrics metrics = new MicrometerCodeReviewProfileMetrics(registry);

    metrics.recordResult(new CodeReviewProfileUpdateResult(
        CodeReviewProfileUpdateResult.Status.UPDATED, 3, 2), Duration.ofMillis(12));
    metrics.recordInvalidOutput();
    metrics.recordStaleRetry();

    assertThat(registry.get("learner.profile.review_consumer").tag("outcome", "updated").counter().count())
        .isEqualTo(1D);
    assertThat(registry.get("learner.profile.review_consumer.version_updates").counter().count()).isEqualTo(2D);
    assertThat(registry.get("learner.profile.review_consumer.window_problems").summary().totalAmount())
        .isEqualTo(3D);
    assertThat(registry.getMeters()).allSatisfy(meter ->
        assertThat(meter.getId().getTags()).extracting(tag -> tag.getKey())
            .noneMatch(key -> key.equals("userId") || key.equals("key") || key.equals("reviewId") || key.equals("tagId")));
  }
}
