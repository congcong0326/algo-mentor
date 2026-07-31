package org.congcong.algomentor.ai.governance.model;

import java.util.Optional;

public enum AiRunSource {
  LEARNING_PLAN_DRAFT(AiBusinessScenario.LEARNING_PLAN_DRAFT),
  LEARNING_PLAN_DRAFT_REVISION(AiBusinessScenario.LEARNING_PLAN_REVISION),
  LEARNING_PLAN_EXTENSION_PROPOSAL(AiBusinessScenario.LEARNING_PLAN_EXTENSION),
  PROBLEM_DETAIL(AiBusinessScenario.TOPIC_EXPLANATION),
  LEARNING_CHAT(AiBusinessScenario.MENTOR_CONVERSATION),
  PRACTICE_CHAT(AiBusinessScenario.PRACTICE_CHAT),
  PRACTICE_CODE_REVIEW(AiBusinessScenario.PRACTICE_CODE_REVIEW),
  LEARNER_MEMORY_DECLARED_UPDATE(AiBusinessScenario.LEARNER_DECLARED_PROFILE_UPDATE),
  LEARNER_PROFILE_CODE_REVIEW_BATCH(AiBusinessScenario.CODE_REVIEW_PROFILE_UPDATE),
  AI_DEBUG(null);

  private final AiBusinessScenario businessScenario;

  AiRunSource(AiBusinessScenario businessScenario) {
    this.businessScenario = businessScenario;
  }

  /** AI Debug 保留在旧治理枚举中，但不属于新的业务场景目录。 */
  public Optional<AiBusinessScenario> businessScenario() {
    return Optional.ofNullable(businessScenario);
  }
}
