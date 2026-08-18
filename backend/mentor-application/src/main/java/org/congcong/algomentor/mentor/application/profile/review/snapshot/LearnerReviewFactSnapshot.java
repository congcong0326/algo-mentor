package org.congcong.algomentor.mentor.application.profile.review.snapshot;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 当前用户全部正式 Code Review 的紧凑、只读事实快照。
 *
 * <p>该对象不携带源码、完整 Review Markdown 或聊天正文。所有计数、版本顺序和恢复状态均由服务端计算。</p>
 */
public record LearnerReviewFactSnapshot(
    Coverage coverage,
    Overall overall,
    List<ProblemReviewTrajectory> problemTrajectories,
    List<TagReviewFacts> tagFacts
) {

  public LearnerReviewFactSnapshot {
    coverage = Objects.requireNonNull(coverage, "coverage");
    overall = Objects.requireNonNull(overall, "overall");
    problemTrajectories = immutable(problemTrajectories, "problem trajectories");
    tagFacts = immutable(tagFacts, "tag facts");
    if (coverage.reviewCount() < 1 || coverage.distinctProblemCount() != problemTrajectories.size()
        || overall.passedReviewCount() + overall.failedReviewCount() != coverage.reviewCount()) {
      throw new IllegalArgumentException("Learner review fact snapshot is inconsistent");
    }
  }

  public enum HistoryDepth {
    EARLY_SAMPLE,
    ESTABLISHED
  }

  public record Coverage(
      int reviewCount,
      int distinctProblemCount,
      Instant earliestReviewAt,
      Instant latestReviewAt,
      HistoryDepth historyDepth
  ) {
    public Coverage {
      if (reviewCount < 1 || distinctProblemCount < 1 || earliestReviewAt == null || latestReviewAt == null
          || earliestReviewAt.isAfter(latestReviewAt) || historyDepth == null) {
        throw new IllegalArgumentException("Learner review fact snapshot coverage is invalid");
      }
    }
  }

  public record Overall(
      int passedReviewCount,
      int failedReviewCount,
      PassCount latestByProblem,
      PassCount firstAttemptByProblem,
      int functionalFailureCount,
      int recoveredFailureCount,
      int unresolvedFailureCount,
      BigDecimal latestAverageScore
  ) {
    public Overall {
      if (passedReviewCount < 0 || failedReviewCount < 0 || latestByProblem == null || firstAttemptByProblem == null
          || functionalFailureCount < 0 || recoveredFailureCount < 0 || unresolvedFailureCount < 0
          || functionalFailureCount != recoveredFailureCount + unresolvedFailureCount || latestAverageScore == null) {
        throw new IllegalArgumentException("Learner review fact snapshot overall facts are invalid");
      }
    }
  }

  /** 通过题目数和参与题目数。 */
  public record PassCount(int passedCount, int totalCount) {
    public PassCount {
      if (passedCount < 0 || totalCount < 0 || passedCount > totalCount) {
        throw new IllegalArgumentException("Learner review pass count is invalid");
      }
    }
  }

  private static <T> List<T> immutable(List<T> values, String field) {
    if (values == null || values.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException("Learner review fact snapshot " + field + " are invalid");
    }
    return List.copyOf(values);
  }
}
