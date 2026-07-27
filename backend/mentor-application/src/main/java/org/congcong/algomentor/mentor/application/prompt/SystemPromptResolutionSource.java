package org.congcong.algomentor.mentor.application.prompt;

/** 解析后的系统提示词正文来源。 */
public enum SystemPromptResolutionSource {
  POLICY,
  CODE_NO_MATCH,
  CODE_POLICY_UNAVAILABLE,
  CODE_RESOLUTION_FAILURE,
  CODE_INVALID_POLICY
}
