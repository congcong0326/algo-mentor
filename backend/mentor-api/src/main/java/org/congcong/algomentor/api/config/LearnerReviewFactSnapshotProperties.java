package org.congcong.algomentor.api.config;

import java.time.Duration;
import org.congcong.algomentor.mentor.application.profile.review.snapshot.LearnerReviewFactSnapshotPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 全量 Review 快照的保守阈值，避免模型自行定义“长期稳定”的样本边界。 */
@ConfigurationProperties(prefix = LearnerReviewFactSnapshotProperties.PREFIX)
public class LearnerReviewFactSnapshotProperties {

  public static final String PREFIX = "algo-mentor.learner-memory.review-fact-snapshot";

  private int establishedMinimumProblemCount =
      LearnerReviewFactSnapshotPolicy.DEFAULT_ESTABLISHED_MINIMUM_PROBLEM_COUNT;
  private Duration establishedMinimumObservationPeriod =
      LearnerReviewFactSnapshotPolicy.DEFAULT_ESTABLISHED_MINIMUM_OBSERVATION_PERIOD;
  private int retainedAttemptsPerProblem = LearnerReviewFactSnapshotPolicy.DEFAULT_RETAINED_ATTEMPTS_PER_PROBLEM;
  private int findingSummaryMaxChars = LearnerReviewFactSnapshotPolicy.DEFAULT_FINDING_SUMMARY_MAX_CHARS;

  public int getEstablishedMinimumProblemCount() {
    return establishedMinimumProblemCount;
  }

  public void setEstablishedMinimumProblemCount(int establishedMinimumProblemCount) {
    this.establishedMinimumProblemCount = establishedMinimumProblemCount;
  }

  public Duration getEstablishedMinimumObservationPeriod() {
    return establishedMinimumObservationPeriod;
  }

  public void setEstablishedMinimumObservationPeriod(Duration establishedMinimumObservationPeriod) {
    this.establishedMinimumObservationPeriod = establishedMinimumObservationPeriod;
  }

  public int getRetainedAttemptsPerProblem() {
    return retainedAttemptsPerProblem;
  }

  public void setRetainedAttemptsPerProblem(int retainedAttemptsPerProblem) {
    this.retainedAttemptsPerProblem = retainedAttemptsPerProblem;
  }

  public int getFindingSummaryMaxChars() {
    return findingSummaryMaxChars;
  }

  public void setFindingSummaryMaxChars(int findingSummaryMaxChars) {
    this.findingSummaryMaxChars = findingSummaryMaxChars;
  }

  public LearnerReviewFactSnapshotPolicy toPolicy() {
    return new LearnerReviewFactSnapshotPolicy(
        establishedMinimumProblemCount,
        establishedMinimumObservationPeriod,
        retainedAttemptsPerProblem,
        findingSummaryMaxChars);
  }
}
