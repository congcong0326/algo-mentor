package org.congcong.algomentor.ai.governance.policy.runtime;

import org.congcong.algomentor.ai.governance.model.AiGovernanceErrorCode;

/** 动态 AI 运行策略读取或校验失败。 */
public class AiRuntimePolicyException extends RuntimeException {

  private final AiGovernanceErrorCode code;

  public AiRuntimePolicyException(AiGovernanceErrorCode code, String message) {
    this(code, message, null);
  }

  public AiRuntimePolicyException(AiGovernanceErrorCode code, String message, Throwable cause) {
    super(message, cause);
    this.code = code == null ? AiGovernanceErrorCode.AI_RUNTIME_SETTINGS_INVALID : code;
  }

  public AiGovernanceErrorCode code() {
    return code;
  }
}
