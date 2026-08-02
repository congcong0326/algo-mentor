package org.congcong.algomentor.api.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.api.problem.model.ProblemDetail;
import org.congcong.algomentor.api.problem.model.ProblemDifficulty;
import org.congcong.algomentor.api.problem.model.ProblemLocale;
import org.congcong.algomentor.api.problem.model.ProblemTag;
import org.congcong.algomentor.api.problem.service.ProblemService;
import org.junit.jupiter.api.Test;

class ProblemServiceReviewProblemCatalogTest {

  @Test
  void findsTheEnglishProblemForAnEnglishLocale() {
    ProblemService problemService = mock(ProblemService.class);
    when(problemService.findProblemBySlug("two-sum", ProblemLocale.EN_US))
        .thenReturn(Optional.of(problem("Two Sum", "# Two Sum\n\nEnglish statement.")));
    ProblemServiceReviewProblemCatalog catalog = new ProblemServiceReviewProblemCatalog(problemService);

    var snapshot = catalog.findBySlug("two-sum", "en-US").orElseThrow();

    assertThat(snapshot.title()).isEqualTo("Two Sum");
    assertThat(snapshot.fullStatementMarkdown()).isEqualTo("# Two Sum\n\nEnglish statement.");
    verify(problemService).findProblemBySlug("two-sum", ProblemLocale.EN_US);
  }

  private ProblemDetail problem(String title, String contentMarkdown) {
    return new ProblemDetail(
        "two-sum",
        1,
        "1",
        title,
        ProblemDifficulty.EASY,
        List.of(new ProblemTag("array", "Array")),
        contentMarkdown,
        "BILINGUAL",
        "https://leetcode.com/problems/two-sum/",
        "[2,7,11,15]\n9",
        "class Solution:\n    pass",
        "abc123",
        null);
  }
}
