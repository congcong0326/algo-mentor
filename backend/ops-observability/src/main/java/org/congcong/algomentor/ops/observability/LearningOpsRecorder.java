package org.congcong.algomentor.ops.observability;

public interface LearningOpsRecorder {

  void learningPlanDraft(OpsStatus status);

  void practiceMessageStream(OpsStatus status);

  void practiceCodeReview(OpsStatus status);

}
