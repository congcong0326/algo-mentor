package org.congcong.algomentor.auth.controller.admin;

import org.congcong.algomentor.auth.loginsettings.service.AuthLoginSettingsErrorCode;
import org.congcong.algomentor.auth.loginsettings.service.AuthLoginSettingsException;
import org.congcong.algomentor.common.api.ApiErrorLocales;
import org.congcong.algomentor.common.api.ApiErrorResponseFactory;
import org.congcong.algomentor.common.api.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AuthLoginSettingsController.class)
public class AuthLoginSettingsExceptionHandler {

  private final ApiErrorResponseFactory responseFactory;

  public AuthLoginSettingsExceptionHandler(ApiErrorResponseFactory responseFactory) {
    this.responseFactory = responseFactory;
  }

  @ExceptionHandler(AuthLoginSettingsException.class)
  public ResponseEntity<ApiResponse<Void>> handle(AuthLoginSettingsException exception,
      jakarta.servlet.http.HttpServletRequest request) {
    HttpStatus status = exception.code() == AuthLoginSettingsErrorCode.AUTH_LOGIN_SETTINGS_INVALID
        ? HttpStatus.BAD_REQUEST
        : HttpStatus.CONFLICT;
    return ResponseEntity.status(status).body(responseFactory.failure(
        exception.code().name(),
        exception.getMessage(),
        ApiErrorLocales.parse(request.getHeader("Accept-Language"))));
  }
}
