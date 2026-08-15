package org.congcong.algomentor.api.practice.repository;

import java.util.List;
import java.util.Objects;
import org.congcong.algomentor.api.practice.mapper.PracticeCodeReviewMapper;
import org.congcong.algomentor.mentor.application.practice.PracticeSubmissionHistoryProblem;
import org.congcong.algomentor.mentor.application.practice.PracticeSubmissionHistoryRepository;

/** PostgreSQL/MyBatis 实现：按用户读取每题最新正式 Review 的 Prompt 索引行。 */
public class MyBatisPracticeSubmissionHistoryRepository implements PracticeSubmissionHistoryRepository {

  private static final int MAX_INDEX_LIMIT = 5;

  private final PracticeCodeReviewMapper mapper;

  public MyBatisPracticeSubmissionHistoryRepository(PracticeCodeReviewMapper mapper) {
    this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
  }

  @Override
  public List<PracticeSubmissionHistoryProblem> findRecentDistinctProblems(long userId, int limit) {
    if (userId < 1 || limit < 1) {
      return List.of();
    }
    return mapper.findRecentSubmittedProblems(userId, boundedLimit(limit)).stream().map(this::toProblem).toList();
  }

  @Override
  public List<PracticeSubmissionHistoryProblem> findRecentDistinctProblemsForSlugs(
      long userId,
      List<String> problemSlugs,
      int limit
  ) {
    List<String> slugs = problemSlugs == null ? List.of() : problemSlugs.stream()
        .filter(slug -> slug != null && !slug.isBlank()).map(String::trim).distinct().toList();
    if (userId < 1 || slugs.isEmpty() || limit < 1) {
      return List.of();
    }
    return mapper.findRecentSubmittedProblemsForSlugs(userId, slugs, boundedLimit(limit)).stream()
        .map(this::toProblem)
        .toList();
  }

  private int boundedLimit(int limit) {
    return Math.min(MAX_INDEX_LIMIT, limit);
  }

  private PracticeSubmissionHistoryProblem toProblem(
      org.congcong.algomentor.api.practice.mapper.model.PracticeSubmissionHistoryProblemRow row
  ) {
    return new PracticeSubmissionHistoryProblem(
        row.reviewId(), row.problemSlug(), row.reviewHistorySummary(), row.createdAt());
  }
}
