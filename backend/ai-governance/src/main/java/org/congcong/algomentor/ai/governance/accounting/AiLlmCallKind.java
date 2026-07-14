package org.congcong.algomentor.ai.governance.accounting;

/** 调用级台账中的真实模型调用来源类型。 */
public enum AiLlmCallKind {
  AGENT_STEP,
  DIRECT,
  BACKGROUND,
  LEGACY_RUN_AGGREGATE
}
