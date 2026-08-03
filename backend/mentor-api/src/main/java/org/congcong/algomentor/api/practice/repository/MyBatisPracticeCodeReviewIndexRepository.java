package org.congcong.algomentor.api.practice.repository;

import java.util.List;
import java.util.Objects;
import org.congcong.algomentor.api.practice.mapper.PracticeCodeReviewMapper;
import org.congcong.algomentor.api.practice.mapper.model.PracticeCodeReviewIndexRow;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewIndexEntry;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewIndexRepository;
import org.congcong.algomentor.mentor.application.review.ReviewContractConstants;

/** PostgreSQL/MyBatis 实现：一次查询读取多个题目的最近代码 Review。 */
public class MyBatisPracticeCodeReviewIndexRepository implements PracticeCodeReviewIndexRepository {

  private static final int PRIMARY_FEEDBACK_MAX_CHARS = 160;

  private final PracticeCodeReviewMapper mapper;

  public MyBatisPracticeCodeReviewIndexRepository(PracticeCodeReviewMapper mapper) {
    this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
  }

  @Override
  public List<PracticeCodeReviewIndexEntry> findRecentByProblemSlugs(
      long userId,
      List<String> problemSlugs,
      int perProblemLimit
  ) {
    if (problemSlugs == null || problemSlugs.isEmpty()) {
      return List.of();
    }
    int limit = Math.min(
        ReviewContractConstants.RECENT_CODE_REVIEW_INDEX_LIMIT,
        Math.max(1, perProblemLimit));
    return mapper.findRecentByProblemSlugs(userId, problemSlugs, limit).stream()
        .map(this::toEntry)
        .toList();
  }

  private PracticeCodeReviewIndexEntry toEntry(PracticeCodeReviewIndexRow row) {
    return new PracticeCodeReviewIndexEntry(
        row.reviewId(),
        row.planId(),
        row.phaseIndex(),
        row.problemSlug(),
        row.practiceSessionId(),
        row.versionNo(),
        row.language(),
        row.contentLocale(),
        row.totalScore(),
        row.passed(),
        normalizePrimaryFeedback(row.primaryFeedback()),
        row.createdAt());
  }

  private String normalizePrimaryFeedback(String feedback) {
    if (feedback == null) {
      return null;
    }
    String normalized = feedback.replaceAll("\\s+", " ").trim();
    if (normalized.isEmpty()) {
      return null;
    }
    return normalized.length() <= PRIMARY_FEEDBACK_MAX_CHARS
        ? normalized
        : normalized.substring(0, PRIMARY_FEEDBACK_MAX_CHARS);
  }
}
