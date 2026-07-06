package org.congcong.algomentor.mentor.application.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReview;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewEvidence;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewScore;
import org.junit.jupiter.api.Test;

class ReviewSeedPolicyTest {

  private final Instant now = Instant.parse("2026-07-02T00:00:00Z");
  private final ReviewSeedPolicy policy = new ReviewSeedPolicy(ReviewSchedulerProperties.defaults());

  @Test
  void failedReviewIsDueImmediately() {
    ReviewSeed seed = policy.forReview(review("5.5", false), false, now);

    assertThat(seed.bucket()).isEqualTo(ReviewSeedBucket.FAILED);
    assertThat(seed.initialRating()).isEqualTo(ReviewRating.AGAIN);
    assertThat(seed.state().repetitions()).isZero();
    assertThat(seed.state().intervalDays()).isZero();
    assertThat(seed.state().fsrsState()).isEqualTo("LEARNING");
    assertThat(seed.state().fsrsStability()).isNull();
    assertThat(seed.state().fsrsDifficulty()).isNull();
    assertThat(seed.dueAt()).isEqualTo(now);
  }

  @Test
  void normalPassedReviewStartsAtThreeDays() {
    ReviewSeed seed = policy.forReview(review("8.0", true), true, now);

    assertThat(seed.bucket()).isEqualTo(ReviewSeedBucket.NORMAL);
    assertThat(seed.initialRating()).isEqualTo(ReviewRating.GOOD);
    assertThat(seed.state().repetitions()).isEqualTo(1);
    assertThat(seed.state().intervalDays()).isEqualTo(3);
    assertThat(seed.state().fsrsState()).isEqualTo("REVIEW");
    assertThat(seed.state().fsrsStability()).isNotNull();
    assertThat(seed.state().fsrsDifficulty()).isNotNull();
    assertThat(seed.dueAt()).isEqualTo(Instant.parse("2026-07-05T00:00:00Z"));
  }

  @Test
  void highScorePassedReviewStartsAtFourDaysWhenProblemIsNotHard() {
    ReviewSeed seed = policy.forReview(review("9.2", true), true, now, "MEDIUM");

    assertThat(seed.bucket()).isEqualTo(ReviewSeedBucket.HIGH_SCORE);
    assertThat(seed.initialRating()).isEqualTo(ReviewRating.EASY);
    assertThat(seed.state().intervalDays()).isGreaterThan(4);
  }

  @Test
  void hardProblemDoesNotUseHighScoreBucket() {
    ReviewSeed seed = policy.forReview(review("9.5", true), true, now, "HARD");

    assertThat(seed.bucket()).isEqualTo(ReviewSeedBucket.NORMAL);
    assertThat(seed.initialRating()).isEqualTo(ReviewRating.GOOD);
    assertThat(seed.state().intervalDays()).isEqualTo(3);
  }

  @Test
  void lowConfidencePassedReviewStartsAtOneDay() {
    PracticeCodeReview review = review("9.5", true, List.of(new PracticeCodeReviewEvidence("HINT_USED", "2")));

    ReviewSeed seed = policy.forReview(review, true, now);

    assertThat(seed.bucket()).isEqualTo(ReviewSeedBucket.LOW_CONFIDENCE);
    assertThat(seed.initialRating()).isEqualTo(ReviewRating.HARD);
    assertThat(seed.lowConfidence()).isTrue();
    assertThat(seed.state().repetitions()).isEqualTo(1);
    assertThat(seed.state().intervalDays()).isEqualTo(1);
    assertThat(seed.dueAt()).isEqualTo(Instant.parse("2026-07-03T00:00:00Z"));
  }

  private PracticeCodeReview review(String totalScore, boolean passed) {
    return review(totalScore, passed, List.of());
  }

  private PracticeCodeReview review(String totalScore, boolean passed, List<PracticeCodeReviewEvidence> evidence) {
    return new PracticeCodeReview(
        10L,
        7L,
        3L,
        1,
        "two-sum",
        50L,
        1,
        100L,
        101L,
        null,
        "class Solution {}",
        "class Solution {}",
        "java",
        evidence,
        "context",
        score(totalScore),
        passed,
        List.of("reason"),
        List.of("suggestion"),
        "review",
        now);
  }

  private PracticeCodeReviewScore score(String totalScore) {
    return new PracticeCodeReviewScore(
        new BigDecimal("4.0"),
        new BigDecimal("2.0"),
        new BigDecimal("2.0"),
        new BigDecimal("1.0"),
        new BigDecimal("1.0"),
        new BigDecimal(totalScore));
  }
}
