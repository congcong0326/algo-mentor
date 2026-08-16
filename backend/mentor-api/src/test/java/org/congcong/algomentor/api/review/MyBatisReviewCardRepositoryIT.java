package org.congcong.algomentor.api.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.congcong.algomentor.api.review.mapper.ProblemReviewCardMapper;
import org.congcong.algomentor.api.review.repository.MyBatisReviewCardRepository;
import org.congcong.algomentor.api.support.PostgresIntegrationTestSupport;
import org.congcong.algomentor.mentor.application.review.card.ReviewCardSource;
import org.congcong.algomentor.mentor.application.review.schedule.FsrsState;
import org.congcong.algomentor.mentor.application.review.schedule.ReviewRating;
import org.congcong.algomentor.mentor.application.review.schedule.ReviewSeed;
import org.congcong.algomentor.mentor.application.review.schedule.ReviewSeedBucket;
import org.congcong.algomentor.mentor.application.review.schedule.SchedulingState;
import org.junit.jupiter.api.Test;

class MyBatisReviewCardRepositoryIT extends PostgresIntegrationTestSupport {

  private static final Instant NOW = Instant.parse("2026-08-16T00:00:00Z");

  @Test
  void resetsSchedulingWhenAPassedReviewIsFollowedByAFailedReview() throws Exception {
    migrateLatest();
    String problemSlug = "remove-duplicates-from-sorted-array";
    insertProblem(problemSlug, 26, List.of("ARRAY"), List.of("Array"), List.of("数组"));
    long userId = insertUser();
    MyBatisReviewCardRepository repository = new MyBatisReviewCardRepository(
        sqlSessionTemplate("mapper/review/ProblemReviewCardMapper.xml")
            .getMapper(ProblemReviewCardMapper.class),
        new ObjectMapper());

    var passed = repository.upsertForReview(
        userId,
        problemSlug,
        ReviewCardSource.REVIEW_PASSED,
        new ObjectMapper().createObjectNode(),
        passedSeed());
    var failed = repository.upsertForReview(
        userId,
        problemSlug,
        ReviewCardSource.REVIEW_FAILED,
        new ObjectMapper().createObjectNode(),
        failedSeed());

    assertThat(passed.lastReviewedAt()).isEqualTo(NOW);
    assertThat(failed.scheduling().fsrsState()).isEqualTo(FsrsState.LEARNING);
    assertThat(failed.scheduling().fsrsStability()).isNull();
    assertThat(failed.scheduling().fsrsDifficulty()).isNull();
    assertThat(failed.scheduling().lapses()).isEqualTo(1);
    assertThat(failed.lastReviewedAt()).isNull();
  }

  private ReviewSeed passedSeed() {
    return new ReviewSeed(
        new SchedulingState(
            1,
            19,
            0,
            FsrsState.REVIEW,
            null,
            new BigDecimal("16.150700"),
            new BigDecimal("2.482439")),
        NOW.plus(19, ChronoUnit.DAYS),
        NOW,
        ReviewSeedBucket.HIGH_SCORE,
        false,
        ReviewRating.EASY);
  }

  private ReviewSeed failedSeed() {
    return new ReviewSeed(
        SchedulingState.initial(),
        NOW,
        null,
        ReviewSeedBucket.FAILED,
        false,
        ReviewRating.AGAIN);
  }
}
