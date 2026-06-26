package org.congcong.algomentor.agent.core.permission;

public final class AgentToolPermissionException extends RuntimeException {

  private final Code code;

  public AgentToolPermissionException(Code code, String message) {
    super(message);
    if (code == null) {
      throw new IllegalArgumentException("Agent tool permission exception code must not be null");
    }
    this.code = code;
  }

  public Code code() {
    return code;
  }

  public enum Code {
    INVALID_DECISION,
    NOT_FOUND,
    FORBIDDEN,
    ALREADY_DECIDED,
    EXPIRED
  }
}
