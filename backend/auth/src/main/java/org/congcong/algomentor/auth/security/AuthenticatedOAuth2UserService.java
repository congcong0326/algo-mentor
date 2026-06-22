package org.congcong.algomentor.auth.security;

import org.congcong.algomentor.auth.service.OAuth2LoginUserService;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;

public class AuthenticatedOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

  private final OAuth2LoginUserService loginUserService;
  private final OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate;

  public AuthenticatedOAuth2UserService(OAuth2LoginUserService loginUserService) {
    this(loginUserService, new DefaultOAuth2UserService());
  }

  public AuthenticatedOAuth2UserService(
      OAuth2LoginUserService loginUserService,
      OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate
  ) {
    this.loginUserService = loginUserService;
    this.delegate = delegate;
  }

  @Override
  public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
    OAuth2User oauth2User = delegate.loadUser(userRequest);
    AuthenticatedUserPrincipal principal = loginUserService.syncGoogleUser(oauth2User.getAttributes());
    return new AuthenticatedOAuth2User(
        principal,
        oauth2User.getAttributes(),
        AuthAuthorities.fromRoles(principal.roles()));
  }
}
