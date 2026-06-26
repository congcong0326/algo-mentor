package org.congcong.algomentor.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

public record ApiError(
    String code,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) String messageKey,
    String message,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, Object> metadata
) {

  public ApiError(String code, String message) {
    this(code, null, message, Map.of());
  }

  public ApiError(String code, String message, Map<String, Object> metadata) {
    this(code, null, message, metadata);
  }

  public ApiError(String code, String messageKey, String message) {
    this(code, messageKey, message, Map.of());
  }

  public ApiError {
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }
}
