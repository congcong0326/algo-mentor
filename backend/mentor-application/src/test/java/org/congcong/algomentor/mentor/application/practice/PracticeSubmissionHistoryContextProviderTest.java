package org.congcong.algomentor.mentor.application.practice;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PracticeSubmissionHistoryContextProviderTest {

  @Test
  void keepsRelatedHistoryOutsideTheRecentWindowAndReusesTheRunLocalReference() {
    PracticeSubmissionHistoryRepository historyRepository = new PracticeSubmissionHistoryRepository() {
      @Override
      public List<PracticeSubmissionHistoryProblem> findRecentDistinctProblems(long userId, int limit) {
        return List.of(problem(11, "recent-a"), problem(12, "recent-b"));
      }

      @Override
      public List<PracticeSubmissionHistoryProblem> findRecentDistinctProblemsForSlugs(
          long userId, List<String> problemSlugs, int limit) {
        assertThat(problemSlugs).containsExactly("recent-a", "older-related");
        assertThat(limit).isEqualTo(3);
        return List.of(problem(9, "older-related"), problem(11, "recent-a"));
      }
    };
    PracticeChatProblemCatalog catalog = (slug, locale) -> Optional.of(new PracticeChatProblemDetail(
        slug, 1, "Title " + slug, "题目 " + slug, "MEDIUM", List.of("Hash Table"), "", "", List.of()));
    PracticeRelatedProblemCatalog related = slug -> List.of("recent-a", "older-related", slug);
    PracticeSubmissionHistoryContextProvider provider = new PracticeSubmissionHistoryContextProvider(
        historyRepository, catalog, related);

    PracticeSubmissionHistoryContext context = provider.provide(7L, "current", "zh-CN");

    assertThat(context.submittedProblems()).extracting(PracticeSubmissionHistoryEntry::title)
        .containsExactly("题目 recent-a", "题目 recent-b");
    assertThat(context.relatedSubmittedProblems()).extracting(PracticeSubmissionHistoryEntry::title)
        .containsExactly("题目 older-related", "题目 recent-a");
    assertThat(context.relatedSubmittedProblems().get(1).problemRef())
        .isEqualTo(context.submittedProblems().get(0).problemRef())
        .startsWith("pp_");
    assertThat(context.relatedSubmittedProblems().get(0).reviewHistorySummary()).isEqualTo("此前提交的结论。");
  }

  private static PracticeSubmissionHistoryProblem problem(long reviewId, String slug) {
    return new PracticeSubmissionHistoryProblem(
        reviewId, slug, "此前提交的结论。", Instant.parse("2026-01-01T00:00:00Z").plusSeconds(reviewId));
  }
}
