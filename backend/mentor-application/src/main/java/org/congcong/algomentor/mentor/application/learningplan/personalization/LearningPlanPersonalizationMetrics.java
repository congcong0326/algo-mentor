package org.congcong.algomentor.mentor.application.learningplan.personalization;

import java.time.Duration;

/** 个性化上下文的低敏观测端口，禁止传入用户或正文标识。 */
public interface LearningPlanPersonalizationMetrics {

  LearningPlanPersonalizationMetrics NOOP = new LearningPlanPersonalizationMetrics() {
  };

  default void recordContextBuild(
      LearningPlanPersonalizationScenario scenario,
      boolean enabled,
      LearningPlanPersonalizationBuildOutcome outcome,
      boolean trimmed,
      Duration duration
  ) {
  }

  default void recordSourceLoad(
      LearningPlanPersonalizationSource source,
      LearningPlanPersonalizationSourceOutcome outcome
  ) {
  }

  default void recordEntryCount(LearningPlanPersonalizationScenario scenario, int entryCount) {
  }

  default void recordTokenEstimate(LearningPlanPersonalizationScenario scenario, int tokenEstimate) {
  }
}
