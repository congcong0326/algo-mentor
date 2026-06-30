package org.congcong.algomentor.identity.controller;

import java.util.Locale;
import org.congcong.algomentor.common.api.ApiErrorResponseFactory;
import org.congcong.algomentor.common.api.ApiResponse;
import org.congcong.algomentor.identity.service.IdentityUserErrorCode;
import org.congcong.algomentor.identity.service.IdentityUserManagementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AdminUserController.class)
public class AdminUserExceptionHandler {

  private final ApiErrorResponseFactory responseFactory;

  public AdminUserExceptionHandler() {
    this(null);
  }

  public AdminUserExceptionHandler(ApiErrorResponseFactory responseFactory) {
    this.responseFactory = responseFactory;
  }

  @ExceptionHandler(IdentityUserManagementException.class)
  public ResponseEntity<ApiResponse<Void>> handleIdentityUserManagementException(
      IdentityUserManagementException exception,
      Locale locale
  ) {
    String code = toApiCode(exception.code());
    ApiResponse<Void> response = responseFactory == null
        ? ApiResponse.failure(code, exception.getMessage())
        : responseFactory.failure(code, exception.getMessage(), locale);
    return ResponseEntity.status(toStatus(exception.code())).body(response);
  }

  @ExceptionHandler({
      BindException.class,
      MethodArgumentNotValidException.class,
      HttpMessageNotReadableException.class
  })
  public ResponseEntity<ApiResponse<Void>> handleInvalidStatusParsing(Locale locale) {
    String code = toApiCode(IdentityUserErrorCode.USER_STATUS_INVALID);
    String message = "用户状态不合法。";
    ApiResponse<Void> response = responseFactory == null
        ? ApiResponse.failure(code, message)
        : responseFactory.failure(code, message, locale);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
  }

  private HttpStatus toStatus(IdentityUserErrorCode code) {
    return switch (code) {
      case USER_NOT_FOUND -> HttpStatus.NOT_FOUND;
      case USER_STATUS_CONFLICT, USER_SELF_OPERATION_FORBIDDEN -> HttpStatus.CONFLICT;
      case USER_STATUS_INVALID -> HttpStatus.BAD_REQUEST;
    };
  }

  private String toApiCode(IdentityUserErrorCode code) {
    return switch (code) {
      case USER_NOT_FOUND -> AdminUserApiContractConstants.USER_NOT_FOUND;
      case USER_STATUS_CONFLICT -> AdminUserApiContractConstants.USER_STATUS_CONFLICT;
      case USER_SELF_OPERATION_FORBIDDEN -> AdminUserApiContractConstants.USER_SELF_OPERATION_FORBIDDEN;
      case USER_STATUS_INVALID -> AdminUserApiContractConstants.USER_STATUS_INVALID;
    };
  }
}
