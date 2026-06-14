package org.congcong.algomentor.llm.core;

import java.util.Map;

public class LlmException extends RuntimeException {

  private final LlmErrorCode code;
  private final LlmProviderId provider;
  private final LlmModelId model;
  private final boolean retryable;
  private final Map<String, Object> metadata;

  public LlmException(LlmErrorCode code, String message) {
    this(code, message, null, null, false, Map.of(), null);
  }

  public LlmException(LlmErrorCode code, String message, Throwable cause) {
    this(code, message, null, null, false, Map.of(), cause);
  }

  public LlmException(
      LlmErrorCode code,
      String message,
      LlmProviderId provider,
      LlmModelId model,
      boolean retryable,
      Map<String, Object> metadata,
      Throwable cause
  ) {
    super(message, cause);
    this.code = normalize(code);
    this.provider = provider;
    this.model = model;
    this.retryable = retryable;
    this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }

  public static LlmException unsupportedCapability(String message, LlmProviderId provider, LlmModelId model) {
    return new LlmException(
        LlmErrorCode.UNSUPPORTED_CAPABILITY,
        message,
        provider,
        model,
        false,
        Map.of(),
        null);
  }

  public LlmErrorCode code() {
    return code;
  }

  public LlmErrorCode getCode() {
    return code;
  }

  public LlmProviderId provider() {
    return provider;
  }

  public LlmModelId model() {
    return model;
  }

  public boolean retryable() {
    return retryable;
  }

  public Map<String, Object> metadata() {
    return metadata;
  }

  private static LlmErrorCode normalize(LlmErrorCode code) {
    return code == null ? LlmErrorCode.UNKNOWN : code;
  }
}
