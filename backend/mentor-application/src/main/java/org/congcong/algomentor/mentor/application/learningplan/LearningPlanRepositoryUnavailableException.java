package org.congcong.algomentor.mentor.application.learningplan;

public class LearningPlanRepositoryUnavailableException extends LearningPlanException {

  public LearningPlanRepositoryUnavailableException() {
    super(
        "LEARNING_PLAN_REPOSITORY_UNAVAILABLE",
        "学习计划仓储不可用。请启用本地数据源配置后再使用学习计划 API。");
  }
}
