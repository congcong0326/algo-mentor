package org.congcong.algomentor.common.api;

import java.time.Instant;
import java.util.Map;

public record ApiResponse<T>(boolean success, T data, ApiError error, Instant timestamp) {

  public static <T> ApiResponse<T> success(T data) {
    return new ApiResponse<>(true, data, null, Instant.now());
  }

  public static <T> ApiResponse<T> failure(String code, String message) {
    return new ApiResponse<>(false, null, new ApiError(code, message), Instant.now());
  }

  public static <T> ApiResponse<T> failure(String code, String message, Map<String, Object> metadata) {
    return new ApiResponse<>(false, null, new ApiError(code, message, metadata), Instant.now());
  }
}
