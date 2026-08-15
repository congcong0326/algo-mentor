package org.congcong.algomentor.api.practice.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.congcong.algomentor.api.practice.mapper.PracticeCodeReviewMapper;
import org.congcong.algomentor.api.practice.mapper.model.PracticeSubmissionHistoryProblemRow;
import org.junit.jupiter.api.Test;

class MyBatisPracticeSubmissionHistoryRepositoryTest {

  @Test
  void mapsLatestProblemRowsAndKeepsTheConfiguredPromptLimit() {
    PracticeCodeReviewMapper mapper = mock(PracticeCodeReviewMapper.class);
    when(mapper.findRecentSubmittedProblems(7L, 5)).thenReturn(List.of(row(90L, "two-sum")));
    MyBatisPracticeSubmissionHistoryRepository repository = new MyBatisPracticeSubmissionHistoryRepository(mapper);

    assertThat(repository.findRecentDistinctProblems(7L, 99)).singleElement().satisfies(problem -> {
      assertThat(problem.reviewId()).isEqualTo(90L);
      assertThat(problem.problemSlug()).isEqualTo("two-sum");
      assertThat(problem.reviewHistorySummary()).isEqualTo("此前遗漏空前缀；当前版本通过。");
    });
  }

  @Test
  void returnsEmptyWithoutCallingTheMapperForEmptyRelatedCandidates() {
    PracticeCodeReviewMapper mapper = mock(PracticeCodeReviewMapper.class);
    MyBatisPracticeSubmissionHistoryRepository repository = new MyBatisPracticeSubmissionHistoryRepository(mapper);

    assertThat(repository.findRecentDistinctProblemsForSlugs(7L, List.of(), 3)).isEmpty();
  }

  private PracticeSubmissionHistoryProblemRow row(long reviewId, String slug) {
    return new PracticeSubmissionHistoryProblemRow(
        reviewId, slug, "此前遗漏空前缀；当前版本通过。", Instant.parse("2026-01-01T00:00:00Z"));
  }
}
