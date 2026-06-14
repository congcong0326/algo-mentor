package org.congcong.algomentor.llm.core;

public class LlmException extends RuntimeException {

  private final LlmErrorCode code;

  public LlmException(LlmErrorCode code, String message) {
    super(message);
    this.code = normalize(code);
  }

  public LlmException(LlmErrorCode code, String message, Throwable cause) {
    super(message, cause);
    this.code = normalize(code);
  }

  public LlmErrorCode code() {
    return code;
  }

  public LlmErrorCode getCode() {
    return code;
  }

  private static LlmErrorCode normalize(LlmErrorCode code) {
    return code == null ? LlmErrorCode.UNKNOWN : code;
  }
}
