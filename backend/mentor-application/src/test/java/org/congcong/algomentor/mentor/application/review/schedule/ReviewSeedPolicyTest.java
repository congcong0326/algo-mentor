package org.congcong.algomentor.mentor.application.review.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReview;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewEvidence;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewScore;
import org.junit.jupiter.api.Test;

class ReviewSeedPolicyTest {

  private static final Instant NOW = Instant.parse("2026-08-16T00:00:00Z");

  @Test
  void seedsHighScorePassedReviewsAtTheConfiguredFourDayInterval() {
    ReviewSeed seed = new ReviewSeedPolicy(ReviewSchedulerProperties.defaults())
        .forReview(passedReview(), true, NOW, "EASY");

    assertThat(seed.state().fsrsState()).isEqualTo(FsrsState.REVIEW);
    assertThat(seed.state().fsrsStep()).isNull();
    assertThat(seed.state().intervalDays()).isEqualTo(4);
    assertThat(seed.state().fsrsStability()).isNotNull();
    assertThat(seed.state().fsrsDifficulty()).isNotNull();
    assertThat(seed.reviewedAt()).isEqualTo(NOW);
    assertThat(seed.dueAt()).isEqualTo(Instant.parse("2026-08-20T00:00:00Z"));
  }

  @Test
  void seedsNormalAndLowConfidencePassedReviewsWithTheirBusinessIntervals() {
    ReviewSeedPolicy policy = new ReviewSeedPolicy(ReviewSchedulerProperties.defaults());

    ReviewSeed normal = policy.forReview(reviewWithScore(BigDecimal.valueOf(8), List.of()), true, NOW, "MEDIUM");
    ReviewSeed lowConfidence = policy.forReview(
        reviewWithScore(
            BigDecimal.valueOf(8),
            List.of(new PracticeCodeReviewEvidence("LOW_CONFIDENCE", "review-confidence"))),
        true,
        NOW,
        "MEDIUM");

    assertThat(normal.bucket()).isEqualTo(ReviewSeedBucket.NORMAL);
    assertThat(normal.state().intervalDays()).isEqualTo(3);
    assertThat(normal.dueAt()).isEqualTo(Instant.parse("2026-08-19T00:00:00Z"));
    assertThat(lowConfidence.bucket()).isEqualTo(ReviewSeedBucket.LOW_CONFIDENCE);
    assertThat(lowConfidence.state().intervalDays()).isEqualTo(1);
    assertThat(lowConfidence.dueAt()).isEqualTo(Instant.parse("2026-08-17T00:00:00Z"));
  }

  @Test
  void seedsFailedReviewsAsImmediatelyDueFirstLearningStep() {
    ReviewSeed seed = new ReviewSeedPolicy(ReviewSchedulerProperties.defaults())
        .forReview(passedReview(), false, NOW, "EASY");

    assertThat(seed.bucket()).isEqualTo(ReviewSeedBucket.FAILED);
    assertThat(seed.state()).isEqualTo(SchedulingState.initial());
    assertThat(seed.dueAt()).isEqualTo(NOW);
    assertThat(seed.reviewedAt()).isNull();
  }

  private PracticeCodeReview passedReview() {
    return reviewWithScore(BigDecimal.TEN, List.of());
  }

  private PracticeCodeReview reviewWithScore(
      BigDecimal totalScore,
      List<PracticeCodeReviewEvidence> evidence
  ) {
    return new PracticeCodeReview(
        900L,
        42L,
        12L,
        1,
        "two-sum",
        50L,
        1,
        701L,
        702L,
        501L,
        "class Solution {}",
        "class Solution {}",
        "java",
        evidence,
        "",
        new PracticeCodeReviewScore(
            totalScore.subtract(BigDecimal.valueOf(6)),
            BigDecimal.valueOf(2),
            BigDecimal.ONE,
            BigDecimal.ONE,
            BigDecimal.ONE,
            totalScore),
        true,
        List.of(),
        List.of(),
        "通过。",
        NOW);
  }
}
