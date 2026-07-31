package org.congcong.algomentor.mentor.application.profile.review.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SubmissionVersionDiffServiceTest {

  private final SubmissionVersionDiffService service = new SubmissionVersionDiffService();

  @Test
  void generatesUnifiedDiffFromNormalizedCode() {
    SubmissionVersionDiff diff = service.diff(
        submission(1, 1, "int sum = 0;\nreturn sum;"),
        submission(2, 2, "int sum = 1;\nreturn sum;"));

    assertThat(diff.truncated()).isFalse();
    assertThat(diff.unifiedDiff()).startsWith("--- submission-v1-1\n+++ submission-v2-2\n@@");
    assertThat(diff.unifiedDiff()).contains("-int sum = 0;", "+int sum = 1;");
  }

  @Test
  void truncatesOnlyBetweenCompleteHunks() {
    StringBuilder from = new StringBuilder();
    StringBuilder to = new StringBuilder();
    for (int index = 0; index < 16; index++) {
      from.append("before-").append(index).append('-').append("a".repeat(900)).append('\n');
      to.append("after-").append(index).append('-').append("b".repeat(900)).append('\n');
      for (int gap = 0; gap < 5; gap++) {
        from.append("same-").append(index).append('-').append(gap).append('\n');
        to.append("same-").append(index).append('-').append(gap).append('\n');
      }
    }

    SubmissionVersionDiff diff = service.diff(submission(1, 1, from.toString()), submission(2, 2, to.toString()));

    assertThat(diff.truncated()).isTrue();
    assertThat(diff.unifiedDiff().length()).isLessThanOrEqualTo(SubmissionVersionDiffService.MAX_DIFF_CHARS);
    assertThat(diff.unifiedDiff()).startsWith("--- submission-v1-1\n+++ submission-v2-2");
    assertThat(diff.unifiedDiff().split("\\n")).filteredOn(line -> line.startsWith("@@"))
        .allMatch(line -> line.endsWith("@@"));
  }

  @Test
  void rejectsCrossProblemOrReverseVersions() {
    assertThatThrownBy(() -> service.diff(submission(1, 2, "a"), submission(2, 1, "b")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.diff(submission(1, 1, "a"),
        new CodeReviewSubmissionVersion(2, "other", 2, "b")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static CodeReviewSubmissionVersion submission(long id, int version, String code) {
    return new CodeReviewSubmissionVersion(id, "two-sum", version, code);
  }
}
