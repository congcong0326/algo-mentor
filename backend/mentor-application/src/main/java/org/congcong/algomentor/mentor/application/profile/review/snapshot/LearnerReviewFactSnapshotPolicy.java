package org.congcong.algomentor.mentor.application.profile.review.snapshot;

import java.time.Duration;

/** 服务端计算 Review 事实快照时使用的受控阈值。 */
public record LearnerReviewFactSnapshotPolicy(
    int establishedMinimumProblemCount,
    Duration establishedMinimumObservationPeriod,
    int retainedAttemptsPerProblem,
    int findingSummaryMaxChars
) {

  public static final int DEFAULT_ESTABLISHED_MINIMUM_PROBLEM_COUNT = 10;
  public static final Duration DEFAULT_ESTABLISHED_MINIMUM_OBSERVATION_PERIOD = Duration.ofDays(14);
  public static final int DEFAULT_RETAINED_ATTEMPTS_PER_PROBLEM = 5;
  public static final int DEFAULT_FINDING_SUMMARY_MAX_CHARS = 360;

  public LearnerReviewFactSnapshotPolicy {
    if (establishedMinimumProblemCount < 1 || establishedMinimumObservationPeriod == null
        || establishedMinimumObservationPeriod.isNegative() || establishedMinimumObservationPeriod.isZero()
        || retainedAttemptsPerProblem < 1 || retainedAttemptsPerProblem > 10
        || findingSummaryMaxChars < 80 || findingSummaryMaxChars > 1_000) {
      throw new IllegalArgumentException("Learner review fact snapshot policy is invalid");
    }
  }

  public static LearnerReviewFactSnapshotPolicy defaults() {
    return new LearnerReviewFactSnapshotPolicy(
        DEFAULT_ESTABLISHED_MINIMUM_PROBLEM_COUNT,
        DEFAULT_ESTABLISHED_MINIMUM_OBSERVATION_PERIOD,
        DEFAULT_RETAINED_ATTEMPTS_PER_PROBLEM,
        DEFAULT_FINDING_SUMMARY_MAX_CHARS);
  }
}
