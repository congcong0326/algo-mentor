package org.congcong.algomentor.mentor.application.learningplan.policy;

@FunctionalInterface
public interface LearningPlanAiRevisionPolicyResolver {
  LearningPlanAiRevisionCapabilities resolve(long userId);

  static LearningPlanAiRevisionPolicyResolver defaults() {
    return ignored -> LearningPlanAiRevisionPolicy.defaults().capabilities();
  }
}
