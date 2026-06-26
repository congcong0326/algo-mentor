package org.congcong.algomentor.common.api;

import java.util.Locale;
import java.util.Map;

/**
 * 统一创建本地化 HTTP API 错误响应。
 */
public class ApiErrorResponseFactory {

  private final ApiErrorMessageResolver messageResolver;

  public ApiErrorResponseFactory(ApiErrorMessageResolver messageResolver) {
    this.messageResolver = messageResolver;
  }

  public <T> ApiResponse<T> failure(String code, String fallbackMessage, Locale locale) {
    return failure(code, null, fallbackMessage, Map.of(), locale);
  }

  public <T> ApiResponse<T> failure(
      String code,
      String fallbackMessage,
      Map<String, Object> metadata,
      Locale locale
  ) {
    return failure(code, null, fallbackMessage, metadata, locale);
  }

  public <T> ApiResponse<T> failure(
      String code,
      String messageKey,
      String fallbackMessage,
      Map<String, Object> metadata,
      Locale locale,
      Object... args
  ) {
    String effectiveMessageKey = messageKey == null || messageKey.isBlank()
        ? ApiErrorMessageKeys.defaultKey(code)
        : messageKey;
    String message = messageResolver.message(code, effectiveMessageKey, fallbackMessage, locale, args);
    return ApiResponse.failureWithMessageKey(code, effectiveMessageKey, message, metadata);
  }
}
