package org.congcong.algomentor.mentor.application.review;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.Objects;

public class MicrometerMistakeReviewMetrics implements MistakeReviewMetrics {

  private final MeterRegistry registry;

  public MicrometerMistakeReviewMetrics(MeterRegistry registry) {
    this.registry = Objects.requireNonNull(registry, "registry must not be null");
  }

  @Override
  public void recordCardGeneration(CardGenerationOutcome outcome) {
    registry.counter("review.card.generate", "outcome", outcome.name()).increment();
  }

  @Override
  public void recordRecallJudge(ReviewRating rating, RecallJudgeOutcome outcome) {
    registry.counter(
        "review.recall.judge",
        "rating", rating == null ? "UNKNOWN" : rating.name(),
        "outcome", outcome.name()).increment();
  }

  @Override
  public void recordSessionSubmit() {
    registry.counter("review.session.submit").increment();
  }

  @Override
  public void recordNoteIngest(MistakeSource source) {
    recordNoteIngest(source, NoteIngestOutcome.UNKNOWN);
  }

  @Override
  public void recordNoteIngest(MistakeSource source, NoteIngestOutcome outcome) {
    registry.counter(
        "review.note.ingest",
        "source", source.name(),
        "outcome", outcome.name()).increment();
  }

  @Override
  public void recordSeed(ReviewSeedBucket bucket) {
    registry.counter("review.seed", "bucket", bucket.name()).increment();
  }
}
