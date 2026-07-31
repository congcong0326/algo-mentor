package org.congcong.algomentor.mentor.application.profile.run.model;

/** Code Review 满批触发时固定记录的 Review 顺序。 */
public record LearnerMemoryUpdateRunReview(long updateRunId, long reviewId, int sequenceNo) {

  public LearnerMemoryUpdateRunReview {
    if (updateRunId <= 0 || reviewId <= 0 || sequenceNo <= 0) {
      throw new IllegalArgumentException("update run review 字段必须为正数。");
    }
  }
}
