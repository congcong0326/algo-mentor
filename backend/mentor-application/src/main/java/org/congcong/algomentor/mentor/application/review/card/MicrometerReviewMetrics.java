package org.congcong.algomentor.mentor.application.review.card;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.Objects;
import org.congcong.algomentor.mentor.application.review.schedule.ReviewSeedBucket;

public class MicrometerReviewMetrics implements ReviewMetrics {

  private final MeterRegistry registry;

  public MicrometerReviewMetrics(MeterRegistry registry) {
    this.registry = Objects.requireNonNull(registry, "registry must not be null");
  }

  @Override
  public void recordAttemptSubmit() {
    registry.counter("review.attempt.submit").increment();
  }

  @Override
  public void recordCardIngest(ReviewCardSource source, ReviewCardIngestOutcome outcome) {
    registry.counter(
        "review.card.ingest",
        "source", source.name(),
        "outcome", outcome.name()).increment();
  }

  @Override
  public void recordSeed(ReviewSeedBucket bucket) {
    registry.counter("review.seed", "bucket", bucket.name()).increment();
  }
}
