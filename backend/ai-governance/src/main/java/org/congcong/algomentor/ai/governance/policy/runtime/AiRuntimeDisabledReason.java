package org.congcong.algomentor.ai.governance.policy.runtime;

/** 有效 AI 状态被关闭时的稳定原因。 */
public enum AiRuntimeDisabledReason {
  DEPLOYMENT,
  GLOBAL,
  USER,
  PURPOSE
}
