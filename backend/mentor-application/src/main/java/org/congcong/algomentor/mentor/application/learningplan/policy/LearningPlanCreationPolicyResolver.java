package org.congcong.algomentor.mentor.application.learningplan.policy;

/** 按用户解析学习计划创建治理策略。 */
@FunctionalInterface
public interface LearningPlanCreationPolicyResolver {

  LearningPlanCreationPolicy resolve(long userId);

  static LearningPlanCreationPolicyResolver defaults() {
    return ignoredUserId -> LearningPlanCreationPolicy.defaults();
  }
}
