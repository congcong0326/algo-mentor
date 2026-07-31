package org.congcong.algomentor.mentor.application.profile.operation.service;

/** 仅暴露稳定低敏失败码，异常消息不包含 claim、代码或消息正文。 */
public final class LearnerMemoryOperationFailure extends RuntimeException {

  private final Code code;

  public LearnerMemoryOperationFailure(Code code) {
    super(code.name());
    this.code = code;
  }

  public Code code() {
    return code;
  }

  public enum Code {
    STALE_SNAPSHOT,
    INVALID_OPERATION,
    INVALID_EVIDENCE,
    SCOPE_LIMIT,
    HARD_LIMIT,
    DUPLICATE_ACTIVE_TEXT,
    UPDATE_RUN_INVALID,
    AGENT_FAILURE
  }
}
