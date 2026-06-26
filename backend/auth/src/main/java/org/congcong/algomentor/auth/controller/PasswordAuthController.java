package org.congcong.algomentor.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.congcong.algomentor.auth.model.CurrentUserResponse;
import org.congcong.algomentor.auth.model.PasswordLoginRequest;
import org.congcong.algomentor.auth.model.PasswordRegisterRequest;
import org.congcong.algomentor.auth.security.AuthenticatedUserPrincipal;
import org.congcong.algomentor.auth.service.AuthPermissionService;
import org.congcong.algomentor.auth.service.PasswordAuthErrorCode;
import org.congcong.algomentor.auth.service.PasswordRegistrationException;
import org.congcong.algomentor.auth.service.PasswordUserService;
import org.congcong.algomentor.common.api.ApiErrorLocales;
import org.congcong.algomentor.common.api.ApiErrorMessageResolver;
import org.congcong.algomentor.common.api.ApiErrorResponseFactory;
import org.congcong.algomentor.common.api.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(AuthApiContractConstants.AUTH_API_BASE_PATH)
public class PasswordAuthController {

  private final PasswordUserService passwordUserService;
  private final AuthenticationManager authenticationManager;
  private final SecurityContextRepository securityContextRepository;
  private final ApiErrorResponseFactory responseFactory;
  private final AuthPermissionService permissionService;

  public PasswordAuthController(
      PasswordUserService passwordUserService,
      AuthenticationManager authenticationManager,
      SecurityContextRepository securityContextRepository,
      AuthPermissionService permissionService
  ) {
    this(
        passwordUserService,
        authenticationManager,
        securityContextRepository,
        new ApiErrorResponseFactory(new ApiErrorMessageResolver()),
        permissionService);
  }

  public PasswordAuthController(
      PasswordUserService passwordUserService,
      AuthenticationManager authenticationManager,
      SecurityContextRepository securityContextRepository,
      ApiErrorResponseFactory responseFactory,
      AuthPermissionService permissionService
  ) {
    this.passwordUserService = passwordUserService;
    this.authenticationManager = authenticationManager;
    this.securityContextRepository = securityContextRepository;
    this.responseFactory = responseFactory;
    this.permissionService = permissionService;
  }

  @PostMapping(AuthApiContractConstants.REGISTER_PATH)
  public ResponseEntity<ApiResponse<CurrentUserResponse>> register(
      @RequestBody PasswordRegisterRequest request,
      HttpServletRequest servletRequest,
      HttpServletResponse servletResponse
  ) {
    try {
      AuthenticatedUserPrincipal principal = passwordUserService.register(
          request == null ? null : request.email(),
          request == null ? null : request.password(),
          request == null ? null : request.displayName());
      Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
          principal,
          null,
          org.congcong.algomentor.auth.security.AuthAuthorities.fromRoles(principal.roles()));
      saveAuthentication(authentication, servletRequest, servletResponse);
      return ResponseEntity.ok(ApiResponse.success(toResponse(principal)));
    } catch (PasswordRegistrationException exception) {
      HttpStatus status = PasswordAuthErrorCode.AUTH_EMAIL_ALREADY_REGISTERED.equals(exception.code())
          ? HttpStatus.CONFLICT
          : HttpStatus.BAD_REQUEST;
      return failure(status, exception.code(), exception.getMessage(), servletRequest);
    }
  }

  @PostMapping(AuthApiContractConstants.LOGIN_PATH)
  public ResponseEntity<ApiResponse<CurrentUserResponse>> login(
      @RequestBody PasswordLoginRequest request,
      HttpServletRequest servletRequest,
      HttpServletResponse servletResponse
  ) {
    try {
      Authentication authentication = authenticationManager.authenticate(
          UsernamePasswordAuthenticationToken.unauthenticated(
              request == null ? null : request.email(),
              request == null ? null : request.password()));
      saveAuthentication(authentication, servletRequest, servletResponse);
      return ResponseEntity.ok(ApiResponse.success(toResponse((AuthenticatedUserPrincipal) authentication.getPrincipal())));
    } catch (AuthenticationException exception) {
      return failure(
          HttpStatus.UNAUTHORIZED,
          PasswordAuthErrorCode.AUTH_INVALID_CREDENTIALS,
          "邮箱或密码错误。",
          servletRequest);
    }
  }

  private void saveAuthentication(
      Authentication authentication,
      HttpServletRequest request,
      HttpServletResponse response
  ) {
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    SecurityContextHolder.setContext(context);
    securityContextRepository.saveContext(context, request, response);
  }

  private CurrentUserResponse toResponse(AuthenticatedUserPrincipal principal) {
    return new CurrentUserResponse(
        principal.userId(),
        principal.email(),
        principal.displayName(),
        principal.avatarUrl(),
        principal.roles(),
        permissionService.permissionsFor(principal.roles()),
        principal.status());
  }

  private ResponseEntity<ApiResponse<CurrentUserResponse>> failure(
      HttpStatus status,
      String code,
      String fallbackMessage,
      HttpServletRequest request
  ) {
    return ResponseEntity.status(status)
        .body(responseFactory.failure(
            code,
            fallbackMessage,
            ApiErrorLocales.parse(request.getHeader("Accept-Language"))));
  }
}
