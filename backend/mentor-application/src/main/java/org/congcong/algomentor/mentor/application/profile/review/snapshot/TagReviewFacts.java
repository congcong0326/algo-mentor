package org.congcong.algomentor.mentor.application.profile.review.snapshot;

/** 由单题轨迹聚合而成的标签事实，同题多版本不会增加题目覆盖数。 */
public record TagReviewFacts(
    long tagId,
    int problemCount,
    int reviewCount,
    LearnerReviewFactSnapshot.PassCount latestByProblem,
    LearnerReviewFactSnapshot.PassCount firstAttemptByProblem,
    int functionalFailureCount,
    int recoveredFailureCount,
    int unresolvedFailureCount
) {

  public TagReviewFacts {
    if (tagId < 1 || problemCount < 1 || reviewCount < problemCount || latestByProblem == null
        || firstAttemptByProblem == null || latestByProblem.totalCount() != problemCount
        || firstAttemptByProblem.totalCount() != problemCount || functionalFailureCount < 0
        || recoveredFailureCount < 0 || unresolvedFailureCount < 0
        || functionalFailureCount != recoveredFailureCount + unresolvedFailureCount) {
      throw new IllegalArgumentException("Tag review facts are invalid");
    }
  }
}
