package org.congcong.algomentor.mentor.application.review;

public interface MistakeReviewMetrics {

  void recordCardGeneration(CardGenerationOutcome outcome);

  void recordRecallJudge(ReviewGrade grade, RecallJudgeOutcome outcome);

  void recordSessionSubmit();

  void recordNoteIngest(MistakeSource source);

  void recordSeed(ReviewSeedBucket bucket);

  MistakeReviewMetrics NOOP = new MistakeReviewMetrics() {
    @Override
    public void recordCardGeneration(CardGenerationOutcome outcome) {
    }

    @Override
    public void recordRecallJudge(ReviewGrade grade, RecallJudgeOutcome outcome) {
    }

    @Override
    public void recordSessionSubmit() {
    }

    @Override
    public void recordNoteIngest(MistakeSource source) {
    }

    @Override
    public void recordSeed(ReviewSeedBucket bucket) {
    }
  };
}
