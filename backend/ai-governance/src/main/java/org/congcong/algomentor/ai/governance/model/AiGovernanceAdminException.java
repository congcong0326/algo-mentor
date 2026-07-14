package org.congcong.algomentor.ai.governance.model;

/** 管理员 AI 治理写入或查询的稳定业务异常。 */
public class AiGovernanceAdminException extends RuntimeException {

  private final AiGovernanceErrorCode code;

  public AiGovernanceAdminException(AiGovernanceErrorCode code, String message) {
    this(code, message, null);
  }

  public AiGovernanceAdminException(AiGovernanceErrorCode code, String message, Throwable cause) {
    super(message, cause);
    this.code = code == null ? AiGovernanceErrorCode.AI_USAGE_QUERY_INVALID : code;
  }

  public AiGovernanceErrorCode code() {
    return code;
  }
}
