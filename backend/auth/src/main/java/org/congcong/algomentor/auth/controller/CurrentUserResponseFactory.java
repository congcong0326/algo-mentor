package org.congcong.algomentor.auth.controller;

import org.congcong.algomentor.auth.model.CurrentUserResponse;
import org.congcong.algomentor.auth.config.AuthProperties;
import org.congcong.algomentor.auth.repository.AuthUserRepository;
import org.congcong.algomentor.auth.security.AuthSessionAuthenticationMethod;
import org.congcong.algomentor.auth.security.AuthenticatedUserPrincipal;
import org.congcong.algomentor.auth.security.CurrentAuthenticationContextResolver;
import org.congcong.algomentor.auth.service.AuthPermissionService;

/**
 * 集中组装登录、注册和当前用户接口共用的用户响应。
 */
public class CurrentUserResponseFactory {

  private final AuthUserRepository authUserRepository;
  private final CurrentAuthenticationContextResolver authenticationContextResolver;
  private final AuthPermissionService permissionService;
  private final AuthProperties authProperties;

  public CurrentUserResponseFactory(
      AuthUserRepository authUserRepository,
      CurrentAuthenticationContextResolver authenticationContextResolver,
      AuthPermissionService permissionService,
      AuthProperties authProperties
  ) {
    this.authUserRepository = authUserRepository;
    this.authenticationContextResolver = authenticationContextResolver;
    this.permissionService = permissionService;
    this.authProperties = authProperties;
  }

  public CurrentUserResponseFactory(
      AuthUserRepository authUserRepository,
      CurrentAuthenticationContextResolver authenticationContextResolver,
      AuthPermissionService permissionService
  ) {
    this(authUserRepository, authenticationContextResolver, permissionService, new AuthProperties());
  }

  public CurrentUserResponseFactory(AuthPermissionService permissionService) {
    this(null, null, permissionService, new AuthProperties());
  }

  public CurrentUserResponse create(AuthenticatedUserPrincipal principal) {
    boolean passwordConfigured = authUserRepository != null
        && authUserRepository.findPasswordCredentialByUserId(principal.userId()).isPresent();
    AuthSessionAuthenticationMethod method = authenticationContextResolver == null
        ? null
        : authenticationContextResolver.resolve()
            .filter(context -> context.principal().userId().equals(principal.userId()))
            .map(context -> context.method())
            .orElse(null);
    return new CurrentUserResponse(
        principal.userId(),
        principal.email(),
        principal.displayName(),
        principal.avatarUrl(),
        principal.roles(),
        permissionService.permissionsFor(principal.roles()),
        principal.status(),
        principal.passwordChangeRequired(),
        passwordConfigured,
        method,
        authProperties.isPasswordLoginEnabled());
  }
}
