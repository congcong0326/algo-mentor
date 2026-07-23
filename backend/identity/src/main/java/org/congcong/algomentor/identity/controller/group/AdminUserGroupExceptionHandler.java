package org.congcong.algomentor.identity.controller.group;

import java.util.Locale;
import org.congcong.algomentor.common.api.ApiErrorResponseFactory;
import org.congcong.algomentor.common.api.ApiResponse;
import org.congcong.algomentor.identity.group.service.UserGroupErrorCode;
import org.congcong.algomentor.identity.group.service.UserGroupManagementException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AdminUserGroupController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AdminUserGroupExceptionHandler {

  private final ApiErrorResponseFactory responseFactory;

  public AdminUserGroupExceptionHandler() {
    this(null);
  }

  public AdminUserGroupExceptionHandler(ApiErrorResponseFactory responseFactory) {
    this.responseFactory = responseFactory;
  }

  @ExceptionHandler(UserGroupManagementException.class)
  public ResponseEntity<ApiResponse<Void>> handleDomain(
      UserGroupManagementException exception,
      Locale locale
  ) {
    return ResponseEntity.status(status(exception.code()))
        .body(failure(exception.code().name(), exception.getMessage(), locale));
  }

  @ExceptionHandler({
      BindException.class,
      MethodArgumentNotValidException.class,
      HttpMessageNotReadableException.class
  })
  public ResponseEntity<ApiResponse<Void>> handleInvalidRequest(Exception exception, Locale locale) {
    String code = exception instanceof HttpMessageNotReadableException
        ? AdminUserGroupApiContractConstants.REQUEST_BODY_INVALID
        : AdminUserGroupApiContractConstants.VALIDATION_FAILED;
    String message = exception instanceof HttpMessageNotReadableException
        ? "请求体不是合法 JSON 或与接口结构不匹配。"
        : "请求参数校验失败。";
    return ResponseEntity.badRequest().body(failure(code, message, locale));
  }

  private HttpStatus status(UserGroupErrorCode code) {
    return switch (code) {
      case USER_GROUP_NOT_FOUND, USER_GROUP_MEMBER_NOT_FOUND -> HttpStatus.NOT_FOUND;
      case USER_GROUP_CODE_CONFLICT,
          USER_GROUP_DISABLED,
          USER_GROUP_DELETE_REQUIRES_DISABLED -> HttpStatus.CONFLICT;
      case USER_GROUP_INVALID_CODE,
          USER_GROUP_INVALID_EXPIRY,
          USER_GROUP_BATCH_LIMIT_EXCEEDED,
          USER_GROUP_INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
    };
  }

  private ApiResponse<Void> failure(String code, String message, Locale locale) {
    return responseFactory == null
        ? ApiResponse.failure(code, message)
        : responseFactory.failure(code, message, locale);
  }
}
