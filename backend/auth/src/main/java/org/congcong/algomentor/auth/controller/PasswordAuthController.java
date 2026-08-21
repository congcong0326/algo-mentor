package org.congcong.algomentor.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.congcong.algomentor.auth.config.AuthProperties;
import org.congcong.algomentor.auth.loginsettings.service.AuthLoginSettingsProvider;
import org.congcong.algomentor.auth.model.CurrentUserResponse;
import org.congcong.algomentor.auth.model.CompletePasswordResetRequest;
import org.congcong.algomentor.auth.model.PasswordLoginRequest;
import org.congcong.algomentor.auth.model.PasswordRegisterRequest;
import org.congcong.algomentor.auth.betaaccess.service.BetaAccessException;
import org.congcong.algomentor.auth.security.AuthenticatedUserPrincipal;
import org.congcong.algomentor.auth.security.AuthenticatedUserResponseHeaders;
import org.congcong.algomentor.auth.security.BetaAccessAuthenticationException;
import org.congcong.algomentor.auth.security.TemporaryPasswordAuthenticationException;
import org.congcong.algomentor.auth.session.policy.AuthSessionPolicyException;
import org.congcong.algomentor.auth.session.policy.AuthSessionPolicyLoginService;
import org.congcong.algomentor.auth.passwordreset.PasswordResetErrorCode;
import org.congcong.algomentor.auth.passwordreset.PasswordResetException;
import org.congcong.algomentor.auth.passwordreset.PasswordResetService;
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
  private final CurrentUserResponseFactory currentUserResponseFactory;
  private final PasswordResetService passwordResetService;
  private final AuthSessionPolicyLoginService sessionPolicyLoginService;
  private final AuthLoginSettingsProvider loginSettingsProvider;

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
        new CurrentUserResponseFactory(permissionService),
        null,
        null,
        new AuthProperties());
  }

  public PasswordAuthController(
      PasswordUserService passwordUserService,
      AuthenticationManager authenticationManager,
      SecurityContextRepository securityContextRepository,
      ApiErrorResponseFactory responseFactory,
      AuthPermissionService permissionService
  ) {
    this(
        passwordUserService,
        authenticationManager,
        securityContextRepository,
        responseFactory,
        new CurrentUserResponseFactory(permissionService),
        null,
        null,
        new AuthProperties());
  }

  public PasswordAuthController(
      PasswordUserService passwordUserService,
      AuthenticationManager authenticationManager,
      SecurityContextRepository securityContextRepository,
      ApiErrorResponseFactory responseFactory,
      AuthPermissionService permissionService,
      PasswordResetService passwordResetService
  ) {
    this(
        passwordUserService,
        authenticationManager,
        securityContextRepository,
        responseFactory,
        new CurrentUserResponseFactory(permissionService),
        passwordResetService,
        null,
        new AuthProperties());
  }

  public PasswordAuthController(
      PasswordUserService passwordUserService,
      AuthenticationManager authenticationManager,
      SecurityContextRepository securityContextRepository,
      ApiErrorResponseFactory responseFactory,
      AuthPermissionService permissionService,
      PasswordResetService passwordResetService,
      AuthSessionPolicyLoginService sessionPolicyLoginService
  ) {
    this(
        passwordUserService,
        authenticationManager,
        securityContextRepository,
        responseFactory,
        new CurrentUserResponseFactory(permissionService),
        passwordResetService,
        sessionPolicyLoginService,
        new AuthProperties());
  }

  public PasswordAuthController(
      PasswordUserService passwordUserService,
      AuthenticationManager authenticationManager,
      SecurityContextRepository securityContextRepository,
      ApiErrorResponseFactory responseFactory,
      CurrentUserResponseFactory currentUserResponseFactory,
      PasswordResetService passwordResetService,
      AuthSessionPolicyLoginService sessionPolicyLoginService
  ) {
    this(
        passwordUserService,
        authenticationManager,
        securityContextRepository,
        responseFactory,
        currentUserResponseFactory,
        passwordResetService,
        sessionPolicyLoginService,
        new AuthProperties());
  }

  public PasswordAuthController(
      PasswordUserService passwordUserService,
      AuthenticationManager authenticationManager,
      SecurityContextRepository securityContextRepository,
      ApiErrorResponseFactory responseFactory,
      CurrentUserResponseFactory currentUserResponseFactory,
      PasswordResetService passwordResetService,
      AuthSessionPolicyLoginService sessionPolicyLoginService,
      AuthProperties authProperties
  ) {
    this(
        passwordUserService,
        authenticationManager,
        securityContextRepository,
        responseFactory,
        currentUserResponseFactory,
        passwordResetService,
        sessionPolicyLoginService,
        AuthLoginSettingsProvider.fromProperties(authProperties));
  }

  public PasswordAuthController(
      PasswordUserService passwordUserService,
      AuthenticationManager authenticationManager,
      SecurityContextRepository securityContextRepository,
      ApiErrorResponseFactory responseFactory,
      CurrentUserResponseFactory currentUserResponseFactory,
      PasswordResetService passwordResetService,
      AuthSessionPolicyLoginService sessionPolicyLoginService,
      AuthLoginSettingsProvider loginSettingsProvider
  ) {
    this.passwordUserService = passwordUserService;
    this.authenticationManager = authenticationManager;
    this.securityContextRepository = securityContextRepository;
    this.responseFactory = responseFactory;
    this.currentUserResponseFactory = currentUserResponseFactory;
    this.passwordResetService = passwordResetService;
    this.sessionPolicyLoginService = sessionPolicyLoginService;
    this.loginSettingsProvider = loginSettingsProvider;
  }

  @PostMapping(AuthApiContractConstants.REGISTER_PATH)
  public ResponseEntity<ApiResponse<CurrentUserResponse>> register(
      @RequestBody PasswordRegisterRequest request,
      HttpServletRequest servletRequest,
      HttpServletResponse servletResponse
  ) {
    var settings = loginSettingsProvider.current();
    if (!settings.passwordLoginEnabled()
        || !settings.passwordRegistrationEnabled()
        || !settings.accountRegistrationEnabled()) {
      return passwordFeatureDisabled(
          PasswordAuthErrorCode.AUTH_PASSWORD_REGISTRATION_DISABLED,
          "当前未开放邮箱密码注册。",
          servletRequest);
    }
    try {
      AuthenticatedUserPrincipal principal = passwordUserService.register(
          request == null ? null : request.email(),
          request == null ? null : request.password(),
          request == null ? null : request.displayName());
      Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
          principal,
          null,
          org.congcong.algomentor.auth.security.AuthAuthorities.fromRoles(principal.roles()));
      saveAuthentication(authentication, servletRequest, servletResponse, true);
      return ResponseEntity.ok(ApiResponse.success(toResponse(principal)));
    } catch (AuthSessionPolicyException exception) {
      return sessionPolicyFailure(exception, servletRequest);
    } catch (PasswordRegistrationException exception) {
      HttpStatus status = PasswordAuthErrorCode.AUTH_EMAIL_ALREADY_REGISTERED.equals(exception.code())
          ? HttpStatus.CONFLICT
          : HttpStatus.BAD_REQUEST;
      return failure(status, exception.code(), exception.getMessage(), servletRequest);
    } catch (BetaAccessException exception) {
      return failure(
          HttpStatus.FORBIDDEN,
          exception.code().name(),
          exception.getMessage(),
          servletRequest);
    }
  }

  @PostMapping(AuthApiContractConstants.LOGIN_PATH)
  public ResponseEntity<ApiResponse<CurrentUserResponse>> login(
      @RequestBody PasswordLoginRequest request,
      HttpServletRequest servletRequest,
      HttpServletResponse servletResponse
  ) {
    if (!loginSettingsProvider.current().passwordLoginEnabled()) {
      return passwordFeatureDisabled(
          PasswordAuthErrorCode.AUTH_PASSWORD_LOGIN_DISABLED,
          "当前未开放邮箱密码登录。",
          servletRequest);
    }
    try {
      Authentication authentication = authenticationManager.authenticate(
          UsernamePasswordAuthenticationToken.unauthenticated(
              request == null ? null : request.email(),
              request == null ? null : request.password()));
      saveAuthentication(authentication, servletRequest, servletResponse, true);
      return ResponseEntity.ok(ApiResponse.success(toResponse((AuthenticatedUserPrincipal) authentication.getPrincipal())));
    } catch (AuthSessionPolicyException exception) {
      return sessionPolicyFailure(exception, servletRequest);
    } catch (BetaAccessAuthenticationException exception) {
      return failure(
          HttpStatus.FORBIDDEN,
          exception.code().name(),
          exception.getMessage(),
          servletRequest);
    } catch (TemporaryPasswordAuthenticationException exception) {
      return failure(
          HttpStatus.UNAUTHORIZED,
          exception.code().name(),
          exception.getMessage(),
          servletRequest);
    } catch (AuthenticationException exception) {
      return failure(
          HttpStatus.UNAUTHORIZED,
          PasswordAuthErrorCode.AUTH_INVALID_CREDENTIALS,
          "邮箱或密码错误。",
          servletRequest);
    }
  }

  @PostMapping(AuthApiContractConstants.COMPLETE_PASSWORD_RESET_PATH)
  public ResponseEntity<ApiResponse<CurrentUserResponse>> completePasswordReset(
      @RequestBody CompletePasswordResetRequest request,
      Authentication authentication,
      HttpServletRequest servletRequest,
      HttpServletResponse servletResponse
  ) {
    try {
      if (passwordResetService == null
          || authentication == null
          || !(authentication.getPrincipal() instanceof AuthenticatedUserPrincipal principal)) {
        throw new PasswordResetException(
            PasswordResetErrorCode.AUTH_PASSWORD_CHANGE_REQUIRED,
            "当前 Session 不需要完成临时密码修改。");
      }
      AuthenticatedUserPrincipal updatedPrincipal = passwordResetService.completeReset(
          principal,
          request == null ? null : request.newPassword(),
          request == null ? null : request.confirmPassword());
      Authentication updatedAuthentication = UsernamePasswordAuthenticationToken.authenticated(
          updatedPrincipal,
          null,
          authentication.getAuthorities());
      saveAuthentication(updatedAuthentication, servletRequest, servletResponse, false);
      return ResponseEntity.ok(ApiResponse.success(toResponse(updatedPrincipal)));
    } catch (PasswordResetException exception) {
      HttpStatus status = exception.code() == PasswordResetErrorCode.AUTH_REQUEST_INVALID
          ? HttpStatus.BAD_REQUEST
          : HttpStatus.FORBIDDEN;
      return failure(status, exception.code().name(), exception.getMessage(), servletRequest);
    }
  }

  private void saveAuthentication(
      Authentication authentication,
      HttpServletRequest request,
      HttpServletResponse response,
      boolean newLogin
  ) {
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    SecurityContextHolder.setContext(context);
    securityContextRepository.saveContext(context, request, response);
    if (authentication.getPrincipal() instanceof AuthenticatedUserPrincipal principal) {
      if (newLogin && sessionPolicyLoginService != null) {
        sessionPolicyLoginService.apply(principal.userId(), request.getSession(false));
      }
      AuthenticatedUserResponseHeaders.write(response, principal);
    }
  }

  private ResponseEntity<ApiResponse<CurrentUserResponse>> sessionPolicyFailure(
      AuthSessionPolicyException exception,
      HttpServletRequest request
  ) {
    SecurityContextHolder.clearContext();
    return failure(HttpStatus.SERVICE_UNAVAILABLE, exception.code().name(), exception.getMessage(), request);
  }

  private ResponseEntity<ApiResponse<CurrentUserResponse>> passwordFeatureDisabled(
      String code,
      String fallbackMessage,
      HttpServletRequest request
  ) {
    return failure(HttpStatus.FORBIDDEN, code, fallbackMessage, request);
  }

  private CurrentUserResponse toResponse(AuthenticatedUserPrincipal principal) {
    return currentUserResponseFactory.create(principal);
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
