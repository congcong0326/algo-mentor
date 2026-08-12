package org.congcong.algomentor.mentor.application.learningplan.policy;

/** 学习计划 AI 修订访问检查的低基数观测端口。 */
public interface LearningPlanAiRevisionAccessMetrics {
  LearningPlanAiRevisionAccessMetrics NOOP = (action, outcome) -> {
  };

  void record(LearningPlanAiRevisionAction action, Outcome outcome);

  enum Outcome {
    ALLOWED,
    DENIED,
    POLICY_UNAVAILABLE
  }
}
