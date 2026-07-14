package org.congcong.algomentor.auth.controller.admin;

import java.util.Locale;
import org.congcong.algomentor.auth.passwordreset.PasswordResetErrorCode;
import org.congcong.algomentor.auth.passwordreset.PasswordResetException;
import org.congcong.algomentor.common.api.ApiErrorResponseFactory;
import org.congcong.algomentor.common.api.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AdminPasswordResetController.class)
public class AdminPasswordResetExceptionHandler {

  private final ApiErrorResponseFactory responseFactory;

  public AdminPasswordResetExceptionHandler(ApiErrorResponseFactory responseFactory) {
    this.responseFactory = responseFactory;
  }

  @ExceptionHandler(PasswordResetException.class)
  public ResponseEntity<ApiResponse<Void>> handle(PasswordResetException exception, Locale locale) {
    return ResponseEntity.status(status(exception.code()))
        .body(responseFactory.failure(exception.code().name(), exception.getMessage(), locale));
  }

  private static HttpStatus status(PasswordResetErrorCode code) {
    return switch (code) {
      case USER_NOT_FOUND -> HttpStatus.NOT_FOUND;
      case AUTH_PASSWORD_CREDENTIAL_NOT_FOUND, AUTH_PASSWORD_RESET_SELF_FORBIDDEN -> HttpStatus.CONFLICT;
      case AUTH_TEMPORARY_PASSWORD_EXPIRED, AUTH_TEMPORARY_PASSWORD_CONSUMED -> HttpStatus.UNAUTHORIZED;
      case AUTH_PASSWORD_CHANGE_REQUIRED -> HttpStatus.FORBIDDEN;
      case AUTH_REQUEST_INVALID -> HttpStatus.BAD_REQUEST;
      case AUTH_PASSWORD_RESET_FAILED -> HttpStatus.INTERNAL_SERVER_ERROR;
    };
  }
}
