package org.congcong.algomentor.auth.security;

import org.congcong.algomentor.auth.service.OAuth2LoginUserService;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

public class AuthenticatedOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

  private final OAuth2LoginUserService loginUserService;
  private final OAuth2UserService<OidcUserRequest, OidcUser> delegate;

  public AuthenticatedOidcUserService(OAuth2LoginUserService loginUserService) {
    this(loginUserService, new OidcUserService());
  }

  public AuthenticatedOidcUserService(
      OAuth2LoginUserService loginUserService,
      OAuth2UserService<OidcUserRequest, OidcUser> delegate
  ) {
    this.loginUserService = loginUserService;
    this.delegate = delegate;
  }

  @Override
  public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
    OidcUser oidcUser = delegate.loadUser(userRequest);
    AuthenticatedUserPrincipal principal = loginUserService.syncGoogleUser(oidcUser.getAttributes());
    return new AuthenticatedOidcUser(
        principal,
        oidcUser,
        AuthAuthorities.fromRoles(principal.roles()));
  }
}
