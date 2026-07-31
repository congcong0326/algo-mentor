package org.congcong.algomentor.mentor.application.profile.review.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewScore;
import org.junit.jupiter.api.Test;

class ReviewTrajectoryServiceTest {

  private final ReviewTrajectoryService service = new ReviewTrajectoryService();

  @Test
  void calculatesStableAdjacentScoreAndFindingChangesInVersionOrder() {
    ReviewTrajectory trajectory = service.calculate(List.of(
        review(12, 2, new BigDecimal("7"), List.of(" Missing null case ", "NESTED loop")),
        review(11, 1, new BigDecimal("5"), List.of("nested   loop", "Off by one")),
        review(13, 3, new BigDecimal("8"), List.of("off by one", "unused value"))));

    assertThat(trajectory.versions()).extracting(version -> version.review().versionNo()).containsExactly(1, 2, 3);
    assertThat(trajectory.versions().get(0).scoreDelta()).isNull();
    assertThat(trajectory.versions().get(1).scoreDelta()).isEqualByComparingTo("2");
    assertThat(trajectory.versions().get(1).persistedFindings()).containsExactly("nested loop");
    assertThat(trajectory.versions().get(1).resolvedFindings()).containsExactly("off by one");
    assertThat(trajectory.versions().get(1).newFindings()).containsExactly("missing null case");
    assertThat(trajectory.versions().get(2).scoreDelta()).isEqualByComparingTo("1");
    assertThat(trajectory.versions().get(2).resolvedFindings()).containsExactly("missing null case", "nested loop");
    assertThat(trajectory.versions().get(2).newFindings()).containsExactly("off by one", "unused value");
  }

  @Test
  void rejectsCrossProblemOrRepeatedVersions() {
    assertThatThrownBy(() -> service.calculate(List.of(
        review(1, 1, BigDecimal.ONE, List.of()),
        new CodeReviewHistory(2, "other-problem", 2, score(BigDecimal.valueOf(2)), false, List.of(), List.of(), List.of(),
            Instant.parse("2026-01-02T00:00:00Z")))))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> service.calculate(List.of(
        review(1, 1, BigDecimal.ONE, List.of()),
        review(2, 1, BigDecimal.valueOf(2), List.of()))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static CodeReviewHistory review(long id, int version, BigDecimal total, List<String> findings) {
    return new CodeReviewHistory(
        id, "two-sum", version, score(total), false, findings, List.of(), List.of(8L),
        Instant.parse("2026-01-0" + version + "T00:00:00Z"));
  }

  private static PracticeCodeReviewScore score(BigDecimal total) {
    return new PracticeCodeReviewScore(
        total.min(new BigDecimal("4")), BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, total);
  }
}
