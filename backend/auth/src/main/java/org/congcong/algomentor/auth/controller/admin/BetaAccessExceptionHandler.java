package org.congcong.algomentor.auth.controller.admin;

import java.util.Locale;
import org.congcong.algomentor.auth.betaaccess.service.BetaAccessErrorCode;
import org.congcong.algomentor.auth.betaaccess.service.BetaAccessException;
import org.congcong.algomentor.common.api.ApiErrorResponseFactory;
import org.congcong.algomentor.common.api.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = BetaAccessController.class)
public class BetaAccessExceptionHandler {

  private final ApiErrorResponseFactory responseFactory;

  public BetaAccessExceptionHandler(ApiErrorResponseFactory responseFactory) {
    this.responseFactory = responseFactory;
  }

  @ExceptionHandler(BetaAccessException.class)
  public ResponseEntity<ApiResponse<Void>> handle(BetaAccessException exception, Locale locale) {
    return ResponseEntity.status(status(exception.code()))
        .body(responseFactory.failure(exception.code().name(), exception.getMessage(), locale));
  }

  private static HttpStatus status(BetaAccessErrorCode code) {
    return switch (code) {
      case AUTH_BETA_ACCESS_DENIED -> HttpStatus.FORBIDDEN;
      case BETA_ACCESS_EMAIL_NOT_FOUND -> HttpStatus.NOT_FOUND;
      case BETA_ACCESS_EMAIL_ALREADY_EXISTS, BETA_ACCESS_SETTINGS_CONFLICT -> HttpStatus.CONFLICT;
      case BETA_ACCESS_EMAIL_INVALID, AUTH_REQUEST_INVALID -> HttpStatus.BAD_REQUEST;
    };
  }
}
