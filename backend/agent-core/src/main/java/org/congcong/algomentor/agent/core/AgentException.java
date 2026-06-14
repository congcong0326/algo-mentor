package org.congcong.algomentor.agent.core;

import java.util.Map;

public class AgentException extends RuntimeException {

  private final AgentErrorCode code;
  private final boolean retryable;
  private final Map<String, Object> metadata;

  public AgentException(AgentErrorCode code, String message) {
    this(code, message, false, Map.of(), null);
  }

  public AgentException(
      AgentErrorCode code,
      String message,
      boolean retryable,
      Map<String, Object> metadata,
      Throwable cause
  ) {
    super(message, cause);
    this.code = code == null ? AgentErrorCode.UNKNOWN : code;
    this.retryable = retryable;
    this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }

  public AgentErrorCode code() {
    return code;
  }

  public boolean retryable() {
    return retryable;
  }

  public Map<String, Object> metadata() {
    return metadata;
  }
}
