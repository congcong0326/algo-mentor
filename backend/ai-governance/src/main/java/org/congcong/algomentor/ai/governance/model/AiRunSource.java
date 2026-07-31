package org.congcong.algomentor.ai.governance.model;

import java.util.Optional;

public enum AiRunSource {
  LEARNING_PLAN_DRAFT(AiBusinessScenario.LEARNING_PLAN_DRAFT),
  LEARNING_PLAN_DRAFT_REVISION(AiBusinessScenario.LEARNING_PLAN_REVISION),
  LEARNING_PLAN_EXTENSION_PROPOSAL(AiBusinessScenario.LEARNING_PLAN_EXTENSION),
  /** 仅用于读取退役主题讲解的历史审计记录。 */
  @Deprecated(since = "2026-07-31", forRemoval = false)
  PROBLEM_DETAIL(null),
  /** 仅用于读取退役普通导师会话的历史审计记录。 */
  @Deprecated(since = "2026-07-31", forRemoval = false)
  LEARNING_CHAT(null),
  PRACTICE_CHAT(AiBusinessScenario.PRACTICE_CHAT),
  PRACTICE_CODE_REVIEW(AiBusinessScenario.PRACTICE_CODE_REVIEW),
  LEARNER_MEMORY_DECLARED_UPDATE(AiBusinessScenario.LEARNER_DECLARED_PROFILE_UPDATE),
  LEARNER_PROFILE_CODE_REVIEW_BATCH(AiBusinessScenario.CODE_REVIEW_PROFILE_UPDATE),
  /** 仅用于读取退役调试控制台的历史审计记录。 */
  @Deprecated(since = "2026-07-31", forRemoval = false)
  AI_DEBUG(null);

  private final AiBusinessScenario businessScenario;

  AiRunSource(AiBusinessScenario businessScenario) {
    this.businessScenario = businessScenario;
  }

  /** 已退役来源仅用于兼容读取历史审计记录，不属于新的业务场景目录。 */
  public Optional<AiBusinessScenario> businessScenario() {
    return Optional.ofNullable(businessScenario);
  }
}
