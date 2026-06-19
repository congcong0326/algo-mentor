package org.congcong.algomentor.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

public record ApiError(
    String code,
    String message,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, Object> metadata
) {

  public ApiError(String code, String message) {
    this(code, message, Map.of());
  }

  public ApiError {
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }
}
