package org.congcong.algomentor.mentor.application.profile.review.snapshot;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.congcong.algomentor.mentor.application.profile.review.LearnerMemoryCodeReviewFact;
import org.junit.jupiter.api.Test;

class LearnerReviewFactSnapshotBuilderTest {

  private static final long ARRAY_TAG_ID = 11L;
  private static final long TWO_POINTER_TAG_ID = 12L;

  @Test
  void calculatesAllHistoryRecoveryAndTagFactsWithoutCountingVersionsAsProblems() {
    LearnerReviewFactSnapshot snapshot = new LearnerReviewFactSnapshotBuilder().build(preProductionSample());

    assertThat(snapshot.coverage().reviewCount()).isEqualTo(9);
    assertThat(snapshot.coverage().distinctProblemCount()).isEqualTo(5);
    assertThat(snapshot.coverage().historyDepth()).isEqualTo(LearnerReviewFactSnapshot.HistoryDepth.EARLY_SAMPLE);
    assertThat(snapshot.overall().passedReviewCount()).isEqualTo(6);
    assertThat(snapshot.overall().failedReviewCount()).isEqualTo(3);
    assertThat(snapshot.overall().latestByProblem()).isEqualTo(new LearnerReviewFactSnapshot.PassCount(5, 5));
    assertThat(snapshot.overall().firstAttemptByProblem()).isEqualTo(new LearnerReviewFactSnapshot.PassCount(3, 5));
    assertThat(snapshot.overall().functionalFailureCount()).isEqualTo(3);
    assertThat(snapshot.overall().recoveredFailureCount()).isEqualTo(3);
    assertThat(snapshot.overall().unresolvedFailureCount()).isZero();

    assertThat(snapshot.problemTrajectories()).extracting(ProblemReviewTrajectory::problemSlug)
        .containsExactly(
            "merge-sorted-array",
            "remove-element",
            "remove-duplicates-from-sorted-array",
            "remove-duplicates-from-sorted-array-ii",
            "majority-element");
    assertThat(snapshot.problemTrajectories()).filteredOn(trajectory -> trajectory.problemSlug().equals("merge-sorted-array"))
        .singleElement().extracting(ProblemReviewTrajectory::currentStatus)
        .isEqualTo(ProblemReviewTrajectory.CurrentStatus.RECOVERED);
    assertThat(snapshot.problemTrajectories()).filteredOn(
            trajectory -> trajectory.problemSlug().equals("remove-duplicates-from-sorted-array"))
        .singleElement().extracting(ProblemReviewTrajectory::currentStatus)
        .isEqualTo(ProblemReviewTrajectory.CurrentStatus.RECOVERED_AFTER_REGRESSION);

    assertThat(snapshot.tagFacts()).filteredOn(tag -> tag.tagId() == ARRAY_TAG_ID).singleElement().satisfies(tag -> {
      assertThat(tag.problemCount()).isEqualTo(5);
      assertThat(tag.latestByProblem()).isEqualTo(new LearnerReviewFactSnapshot.PassCount(5, 5));
      assertThat(tag.firstAttemptByProblem()).isEqualTo(new LearnerReviewFactSnapshot.PassCount(3, 5));
      assertThat(tag.functionalFailureCount()).isEqualTo(3);
      assertThat(tag.recoveredFailureCount()).isEqualTo(3);
      assertThat(tag.unresolvedFailureCount()).isZero();
    });
    assertThat(snapshot.tagFacts()).filteredOn(tag -> tag.tagId() == TWO_POINTER_TAG_ID).singleElement().satisfies(tag -> {
      assertThat(tag.problemCount()).isEqualTo(4);
      assertThat(tag.latestByProblem()).isEqualTo(new LearnerReviewFactSnapshot.PassCount(4, 4));
      assertThat(tag.firstAttemptByProblem()).isEqualTo(new LearnerReviewFactSnapshot.PassCount(2, 4));
      assertThat(tag.functionalFailureCount()).isEqualTo(3);
      assertThat(tag.recoveredFailureCount()).isEqualTo(3);
      assertThat(tag.unresolvedFailureCount()).isZero();
    });
  }

  private List<LearnerMemoryCodeReviewFact> preProductionSample() {
    Instant start = Instant.parse("2026-08-16T09:00:00Z");
    return List.of(
        fact(1L, "merge-sorted-array", 1, false, start, "遗漏剩余元素边界", List.of(ARRAY_TAG_ID, TWO_POINTER_TAG_ID)),
        fact(2L, "merge-sorted-array", 2, true, start.plusSeconds(600), "补充剩余元素", List.of(ARRAY_TAG_ID, TWO_POINTER_TAG_ID)),
        fact(3L, "remove-element", 1, false, start.plusSeconds(1_200), "保留方向与题意相反", List.of(ARRAY_TAG_ID, TWO_POINTER_TAG_ID)),
        fact(4L, "remove-element", 2, true, start.plusSeconds(1_800), "保留方向已修正", List.of(ARRAY_TAG_ID, TWO_POINTER_TAG_ID)),
        fact(5L, "remove-duplicates-from-sorted-array", 1, true, start.plusSeconds(2_400), "", List.of(ARRAY_TAG_ID, TWO_POINTER_TAG_ID)),
        fact(6L, "remove-duplicates-from-sorted-array", 2, false, start.plusSeconds(3_000), "slow 指针维护错误", List.of(ARRAY_TAG_ID, TWO_POINTER_TAG_ID)),
        fact(7L, "remove-duplicates-from-sorted-array", 3, true, start.plusSeconds(3_600), "slow 指针已修正", List.of(ARRAY_TAG_ID, TWO_POINTER_TAG_ID)),
        fact(8L, "remove-duplicates-from-sorted-array-ii", 1, true, start.plusSeconds(4_200), "", List.of(ARRAY_TAG_ID, TWO_POINTER_TAG_ID)),
        fact(9L, "majority-element", 1, true, start.plusSeconds(4_800), "命名建议", List.of(ARRAY_TAG_ID)));
  }

  private LearnerMemoryCodeReviewFact fact(
      long reviewId,
      String slug,
      int version,
      boolean passed,
      Instant createdAt,
      String finding,
      List<Long> tags
  ) {
    BigDecimal score = passed ? new BigDecimal("10") : new BigDecimal("5");
    return new LearnerMemoryCodeReviewFact(
        reviewId,
        slug,
        version,
        score,
        score,
        BigDecimal.ONE,
        BigDecimal.ONE,
        BigDecimal.ONE,
        BigDecimal.ONE,
        passed,
        finding.isBlank() ? List.of() : List.of(finding),
        List.of(),
        tags,
        createdAt);
  }
}
