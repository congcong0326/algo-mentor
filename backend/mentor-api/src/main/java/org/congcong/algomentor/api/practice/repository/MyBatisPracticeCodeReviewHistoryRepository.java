package org.congcong.algomentor.api.practice.repository;

import java.util.List;
import java.util.Objects;
import org.congcong.algomentor.api.practice.mapper.PracticeCodeReviewMapper;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewHistoricalFact;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewHistoryRepository;

/** PostgreSQL/MyBatis 适配器：按真实提交时间读取同题跨计划历史窄行。 */
public class MyBatisPracticeCodeReviewHistoryRepository implements PracticeCodeReviewHistoryRepository {

  private static final int MAX_HISTORY_LIMIT = 4;
  private static final int PRIMARY_FINDING_MAX_CHARS = 200;

  private final PracticeCodeReviewMapper mapper;

  public MyBatisPracticeCodeReviewHistoryRepository(PracticeCodeReviewMapper mapper) {
    this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
  }

  @Override
  public List<PracticeCodeReviewHistoricalFact> findRecentForProblem(long userId, String problemSlug, int limit) {
    if (userId < 1 || problemSlug == null || problemSlug.isBlank() || limit < 1) {
      return List.of();
    }
    return mapper.findRecentHistoryFactsForProblem(
            userId, problemSlug.trim(), Math.min(limit, MAX_HISTORY_LIMIT)).stream()
        .map(row -> new PracticeCodeReviewHistoricalFact(
            row.reviewId(), row.passed(), normalizeFinding(row.primaryFinding()), row.createdAt()))
        .toList();
  }

  private String normalizeFinding(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.replaceAll("\\s+", " ").trim();
    if (normalized.isEmpty()) {
      return null;
    }
    return normalized.length() <= PRIMARY_FINDING_MAX_CHARS
        ? normalized
        : normalized.substring(0, PRIMARY_FINDING_MAX_CHARS).strip();
  }
}
