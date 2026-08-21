package org.congcong.algomentor.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.congcong.algomentor.auth.model.UserPasswordUpdateRequest;
import org.congcong.algomentor.auth.model.UserPasswordUpdateResponse;
import org.congcong.algomentor.auth.password.UserPasswordErrorCode;
import org.congcong.algomentor.auth.password.UserPasswordException;
import org.congcong.algomentor.auth.password.UserPasswordService;
import org.congcong.algomentor.auth.password.UserPasswordUpdateCommand;
import org.congcong.algomentor.auth.password.UserPasswordUpdateResult;
import org.congcong.algomentor.common.api.ApiErrorLocales;
import org.congcong.algomentor.common.api.ApiErrorMessageResolver;
import org.congcong.algomentor.common.api.ApiErrorResponseFactory;
import org.congcong.algomentor.common.api.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(AuthApiContractConstants.AUTH_API_BASE_PATH)
public class UserPasswordController {

  private final UserPasswordService userPasswordService;
  private final ApiErrorResponseFactory responseFactory;
  public UserPasswordController(UserPasswordService userPasswordService) {
    this(userPasswordService, new ApiErrorResponseFactory(new ApiErrorMessageResolver()));
  }

  public UserPasswordController(
      UserPasswordService userPasswordService,
      ApiErrorResponseFactory responseFactory
  ) {
    this.userPasswordService = userPasswordService;
    this.responseFactory = responseFactory;
  }

  @PutMapping(AuthApiContractConstants.PASSWORD_PATH)
  public ResponseEntity<ApiResponse<UserPasswordUpdateResponse>> updatePassword(
      @RequestBody(required = false) UserPasswordUpdateRequest request,
      HttpServletRequest servletRequest
  ) {
    try {
      HttpSession session = servletRequest.getSession(false);
      UserPasswordUpdateResult result = userPasswordService.updatePassword(new UserPasswordUpdateCommand(
          request == null ? null : request.currentPassword(),
          request == null ? null : request.newPassword(),
          request == null ? null : request.confirmPassword(),
          session == null ? null : session.getId()));
      return ResponseEntity.ok(ApiResponse.success(new UserPasswordUpdateResponse(
          result.passwordConfigured(),
          result.operation(),
          result.revokedSessionCount())));
    } catch (UserPasswordException exception) {
      return ResponseEntity.status(statusFor(exception.code())).body(responseFactory.failure(
          exception.code().name(),
          exception.getMessage(),
          ApiErrorLocales.parse(servletRequest.getHeader("Accept-Language"))));
    }
  }

  private static HttpStatus statusFor(UserPasswordErrorCode code) {
    return switch (code) {
      case AUTH_PASSWORD_REQUEST_INVALID, AUTH_CURRENT_PASSWORD_REQUIRED -> HttpStatus.BAD_REQUEST;
      case AUTH_PASSWORD_LOGIN_DISABLED,
          AUTH_CURRENT_PASSWORD_INVALID,
          AUTH_PASSWORD_CHANGE_REQUIRED,
          AUTH_PASSWORD_UPDATE_NOT_ALLOWED -> HttpStatus.FORBIDDEN;
      case AUTH_PASSWORD_LOGIN_EMAIL_UNAVAILABLE,
          AUTH_PASSWORD_CHANGED_CONCURRENTLY -> HttpStatus.CONFLICT;
      case AUTH_PASSWORD_UPDATE_FAILED -> HttpStatus.SERVICE_UNAVAILABLE;
    };
  }
}
