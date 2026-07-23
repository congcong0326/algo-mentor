package org.congcong.algomentor.policy.controller;

import java.util.Locale;
import org.congcong.algomentor.common.api.ApiErrorResponseFactory;
import org.congcong.algomentor.common.api.ApiResponse;
import org.congcong.algomentor.policy.controller.admin.AdminGenericPolicyController;
import org.congcong.algomentor.policy.controller.admin.AdminGenericPolicyOrderController;
import org.congcong.algomentor.policy.controller.runtime.EffectiveGenericPolicyController;
import org.congcong.algomentor.policy.service.GenericPolicyErrorCode;
import org.congcong.algomentor.policy.service.GenericPolicyException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 策略 API 的稳定 HTTP 错误码映射。 */
@RestControllerAdvice(assignableTypes = {
    AdminGenericPolicyController.class,
    AdminGenericPolicyOrderController.class,
    EffectiveGenericPolicyController.class
})
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class GenericPolicyExceptionHandler {

  private final ApiErrorResponseFactory responseFactory;

  public GenericPolicyExceptionHandler(ApiErrorResponseFactory responseFactory) {
    this.responseFactory = responseFactory;
  }

  @ExceptionHandler(GenericPolicyException.class)
  public ResponseEntity<ApiResponse<Void>> handle(GenericPolicyException exception, Locale locale) {
    return ResponseEntity.status(status(exception.code()))
        .body(failure(exception.code().name(), exception.getMessage(), locale));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiResponse<Void>> invalidJson(HttpMessageNotReadableException exception, Locale locale) {
    return ResponseEntity.badRequest()
        .body(failure(GenericPolicyErrorCode.POLICY_INVALID_REQUEST.name(), "请求体不是合法 JSON 或与接口结构不匹配。", locale));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiResponse<Void>> invalidArgument(IllegalArgumentException exception, Locale locale) {
    return ResponseEntity.badRequest()
        .body(failure(GenericPolicyErrorCode.POLICY_INVALID_REQUEST.name(), exception.getMessage(), locale));
  }

  private HttpStatus status(GenericPolicyErrorCode errorCode) {
    return switch (errorCode) {
      case POLICY_NOT_FOUND -> HttpStatus.NOT_FOUND;
      case POLICY_LIMIT_EXCEEDED,
          POLICY_VERSION_CONFLICT,
          POLICY_ORDER_CONFLICT -> HttpStatus.CONFLICT;
      case POLICY_RESOLUTION_FAILED -> HttpStatus.INTERNAL_SERVER_ERROR;
      default -> HttpStatus.BAD_REQUEST;
    };
  }

  private ApiResponse<Void> failure(String code, String message, Locale locale) {
    return responseFactory == null
        ? ApiResponse.failure(code, message)
        : responseFactory.failure(code, message, locale);
  }
}
