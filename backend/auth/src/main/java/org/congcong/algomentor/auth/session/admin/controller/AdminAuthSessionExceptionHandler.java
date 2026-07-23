package org.congcong.algomentor.auth.session.admin.controller;

import java.util.Locale;
import java.util.Map;
import org.congcong.algomentor.auth.session.admin.service.AuthSessionAdminErrorCode;
import org.congcong.algomentor.auth.session.admin.service.AuthSessionAdminException;
import org.congcong.algomentor.common.api.ApiErrorResponseFactory;
import org.congcong.algomentor.common.api.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AdminAuthSessionController.class)
public class AdminAuthSessionExceptionHandler {

  private final ApiErrorResponseFactory responseFactory;

  public AdminAuthSessionExceptionHandler(ApiErrorResponseFactory responseFactory) {
    this.responseFactory = responseFactory;
  }

  @ExceptionHandler(AuthSessionAdminException.class)
  public ResponseEntity<ApiResponse<Void>> handle(AuthSessionAdminException exception, Locale locale) {
    return ResponseEntity.status(status(exception.code()))
        .body(responseFactory.failure(
            exception.code().name(),
            messageKey(exception.code()),
            exception.getMessage(),
            Map.of(),
            locale));
  }

  private static String messageKey(AuthSessionAdminErrorCode code) {
    return code == AuthSessionAdminErrorCode.AUTH_SESSION_CURRENT_REVOKE_FORBIDDEN
        ? AdminAuthSessionApiContractConstants.CURRENT_REVOKE_FORBIDDEN_MESSAGE_KEY
        : null;
  }

  private static HttpStatus status(AuthSessionAdminErrorCode code) {
    return switch (code) {
      case AUTH_SESSION_QUERY_INVALID, AUTH_SESSION_REF_INVALID -> HttpStatus.BAD_REQUEST;
      case AUTH_SESSION_CURRENT_REVOKE_FORBIDDEN -> HttpStatus.CONFLICT;
      case AUTH_SESSION_MANAGEMENT_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
      case AUTH_SESSION_REVOKE_FAILED -> HttpStatus.INTERNAL_SERVER_ERROR;
    };
  }
}
