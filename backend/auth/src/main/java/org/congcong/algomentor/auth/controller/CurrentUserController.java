package org.congcong.algomentor.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.congcong.algomentor.auth.model.CurrentUserResponse;
import org.congcong.algomentor.auth.security.AuthDiagnosticSupport;
import org.congcong.algomentor.auth.security.CurrentUserIdProvider;
import org.congcong.algomentor.auth.service.AuthPermissionService;
import org.congcong.algomentor.common.api.ApiErrorLocales;
import org.congcong.algomentor.common.api.ApiErrorMessageResolver;
import org.congcong.algomentor.common.api.ApiErrorResponseFactory;
import org.congcong.algomentor.common.api.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(AuthApiContractConstants.AUTH_API_BASE_PATH)
public class CurrentUserController {

  private static final Logger log = LoggerFactory.getLogger(CurrentUserController.class);

  private final CurrentUserIdProvider currentUserIdProvider;
  private final ApiErrorResponseFactory responseFactory;
  private final AuthPermissionService permissionService;

  public CurrentUserController(CurrentUserIdProvider currentUserIdProvider) {
    this(
        currentUserIdProvider,
        new ApiErrorResponseFactory(new ApiErrorMessageResolver()),
        new AuthPermissionService());
  }

  public CurrentUserController(
      CurrentUserIdProvider currentUserIdProvider,
      ApiErrorResponseFactory responseFactory
  ) {
    this(currentUserIdProvider, responseFactory, new AuthPermissionService());
  }

  public CurrentUserController(
      CurrentUserIdProvider currentUserIdProvider,
      ApiErrorResponseFactory responseFactory,
      AuthPermissionService permissionService
  ) {
    this.currentUserIdProvider = currentUserIdProvider;
    this.responseFactory = responseFactory;
    this.permissionService = permissionService;
  }

  @GetMapping(AuthApiContractConstants.ME_PATH)
  public ResponseEntity<ApiResponse<CurrentUserResponse>> me(HttpServletRequest request) {
    log.info("Current user endpoint called. {}", AuthDiagnosticSupport.requestSummary(request));
    return currentUserIdProvider.currentUser()
        .map(principal -> {
          log.info("Current user endpoint returning authenticated user. userId={}", principal.userId());
          return ResponseEntity.ok(ApiResponse.success(new CurrentUserResponse(
              principal.userId(),
              principal.email(),
              principal.displayName(),
              principal.avatarUrl(),
              principal.roles(),
              permissionService.permissionsFor(principal.roles()),
              principal.status())));
        })
        .orElseGet(() -> {
          log.info("Current user endpoint returning unauthenticated response.");
          return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
              .body(responseFactory.failure(
                  AuthApiContractConstants.AUTH_UNAUTHENTICATED_CODE,
                  "当前请求未登录或无法解析当前用户。",
                  ApiErrorLocales.parse(request.getHeader("Accept-Language"))));
        });
  }
}
