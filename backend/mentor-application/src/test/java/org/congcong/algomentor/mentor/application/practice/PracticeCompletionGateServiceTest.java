package org.congcong.algomentor.mentor.application.practice;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PracticeCompletionGateServiceTest {

  @Test
  void blocksWhenNoReviewExists() {
    PracticeCompletionGate gate = service(Optional.empty())
        .evaluate(7L, session(PracticeProgressStatus.IN_PROGRESS));

    assertThat(gate.canComplete()).isFalse();
    assertThat(gate.reasonCode()).isEqualTo(PracticeCompletionGate.ReasonCode.NO_REVIEW);
    assertThat(gate.passScore()).isEqualByComparingTo("6.0");
  }

  @Test
  void blocksWhenLatestReviewFailed() {
    PracticeCompletionGate gate = service(Optional.of(summary(5, false)))
        .evaluate(7L, session(PracticeProgressStatus.IN_PROGRESS));

    assertThat(gate.canComplete()).isFalse();
    assertThat(gate.reasonCode()).isEqualTo(PracticeCompletionGate.ReasonCode.LATEST_REVIEW_FAILED);
    assertThat(gate.latestScore()).contains(new BigDecimal("5.0"));
  }

  @Test
  void blocksWhenLatestReviewScorePassedButReviewFailed() {
    PracticeCompletionGate gate = service(Optional.of(summary(7, false)))
        .evaluate(7L, session(PracticeProgressStatus.IN_PROGRESS));

    assertThat(gate.canComplete()).isFalse();
    assertThat(gate.reasonCode()).isEqualTo(PracticeCompletionGate.ReasonCode.LATEST_REVIEW_FAILED);
    assertThat(gate.latestScore()).contains(new BigDecimal("7.0"));
  }

  @Test
  void allowsWhenLatestReviewPassed() {
    PracticeCompletionGate gate = service(Optional.of(summary(7, true)))
        .evaluate(7L, session(PracticeProgressStatus.IN_PROGRESS));

    assertThat(gate.canComplete()).isTrue();
    assertThat(gate.reasonCode()).isEqualTo(PracticeCompletionGate.ReasonCode.PASSED);
  }

  @Test
  void alreadyCompletedDoesNotOfferCompletion() {
    PracticeCompletionGate gate = service(Optional.of(summary(7, true)))
        .evaluate(7L, session(PracticeProgressStatus.COMPLETED));

    assertThat(gate.canComplete()).isFalse();
    assertThat(gate.reasonCode()).isEqualTo(PracticeCompletionGate.ReasonCode.ALREADY_COMPLETED);
  }

  @Test
  void recordsCompletionGateMetricsByReasonAndDecision() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    PracticeCompletionGateService service = new PracticeCompletionGateService(
        new FakeReviewRepository(Optional.of(summary(5, false))),
        new MicrometerPracticeCodeReviewMetrics(registry));

    service.evaluate(7L, session(PracticeProgressStatus.IN_PROGRESS));

    assertThat(registry.find("practice.completion_gate.evaluations")
        .tag("canComplete", "false")
        .tag("reason", PracticeCompletionGate.ReasonCode.LATEST_REVIEW_FAILED.name())
        .counter()
        .count()).isEqualTo(1.0);
  }

  private PracticeCompletionGateService service(Optional<PracticeCodeReviewSummary> latest) {
    return new PracticeCompletionGateService(new FakeReviewRepository(latest));
  }

  private PracticeSession session(PracticeProgressStatus status) {
    return new PracticeSession(50L, 7L, 12L, 1, "two-sum", PracticeSessionStatus.ACTIVE, 100L, 200L,
        status, null, Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"), "zh-CN");
  }

  private PracticeCodeReviewSummary summary(int score, boolean passed) {
    return new PracticeCodeReviewSummary(90L, 2, "java", new BigDecimal(score + ".0"), passed,
        Instant.parse("2026-01-02T00:00:00Z"));
  }

  private record FakeReviewRepository(Optional<PracticeCodeReviewSummary> latest)
      implements PracticeCodeReviewRepository {
    @Override
    public PracticeCodeReview save(PracticeCodeReviewDraft draft) {
      throw new UnsupportedOperationException("save not used");
    }

    @Override
    public Optional<PracticeCodeReviewSummary> findLatestSummary(long userId, long sessionId) {
      return latest;
    }

    @Override
    public Optional<PracticeCodeReview> findLatest(long userId, long sessionId) {
      return Optional.empty();
    }

    @Override
    public List<PracticeCodeReviewSummary> findSummaries(long userId, long sessionId) {
      return List.of();
    }

    @Override
    public Optional<PracticeCodeReview> findById(long userId, long sessionId, long reviewId) {
      return Optional.empty();
    }

    @Override
    public Optional<PracticeCodeReview> findByUserMessage(long userId, long sessionId, long userMessageId) {
      return Optional.empty();
    }
  }
}
