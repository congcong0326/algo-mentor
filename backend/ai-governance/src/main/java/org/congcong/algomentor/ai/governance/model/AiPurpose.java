package org.congcong.algomentor.ai.governance.model;

public enum AiPurpose {
  LEARNING_PLAN,
  /** 仅用于读取退役主题讲解的历史审计记录，不再作为可执行业务 purpose。 */
  @Deprecated(since = "2026-07-31", forRemoval = false)
  PROBLEM_EXPLANATION,
  LEARNING_CHAT
}
