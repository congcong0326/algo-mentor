package org.congcong.algomentor.auth.security;

import java.util.LinkedHashMap;
import java.util.Map;
import org.congcong.algomentor.auth.github.GitHubEmailClient;
import org.congcong.algomentor.auth.github.RestClientGitHubEmailClient;
import org.congcong.algomentor.auth.model.OAuthProvider;
import org.congcong.algomentor.auth.service.OAuth2LoginUserService;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;

public class AuthenticatedOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

  public static final String UNSUPPORTED_PROVIDER_CODE = "unsupported_oauth_provider";
  public static final String GITHUB_EMAIL_LOOKUP_FAILED_CODE = "github_email_lookup_failed";
  public static final String GITHUB_VERIFIED_EMAIL_MISSING_CODE = "github_verified_email_missing";

  private final OAuth2LoginUserService loginUserService;
  private final OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate;
  private final GitHubEmailClient gitHubEmailClient;

  public AuthenticatedOAuth2UserService(OAuth2LoginUserService loginUserService) {
    this(loginUserService, new DefaultOAuth2UserService(), new RestClientGitHubEmailClient());
  }

  public AuthenticatedOAuth2UserService(
      OAuth2LoginUserService loginUserService,
      OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate
  ) {
    this(loginUserService, delegate, new RestClientGitHubEmailClient());
  }

  public AuthenticatedOAuth2UserService(
      OAuth2LoginUserService loginUserService,
      OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate,
      GitHubEmailClient gitHubEmailClient
  ) {
    this.loginUserService = loginUserService;
    this.delegate = delegate;
    this.gitHubEmailClient = gitHubEmailClient;
  }

  @Override
  public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
    OAuth2User oauth2User = delegate.loadUser(userRequest);
    OAuthProvider provider = requireProvider(userRequest);
    Map<String, Object> attributes = new LinkedHashMap<>(oauth2User.getAttributes());
    if (provider == OAuthProvider.GITHUB) {
      attributes.put(provider.emailAttribute(), loadGitHubEmail(userRequest));
    }
    AuthenticatedUserPrincipal principal = loginUserService.syncOAuthUser(provider, attributes);
    return new AuthenticatedOAuth2User(
        principal,
        attributes,
        AuthAuthorities.fromRoles(principal.roles()));
  }

  private OAuthProvider requireProvider(OAuth2UserRequest userRequest) {
    String registrationId = userRequest.getClientRegistration().getRegistrationId();
    return OAuthProvider.fromRegistrationId(registrationId)
        .orElseThrow(() -> authenticationException(
            UNSUPPORTED_PROVIDER_CODE,
            "OAuth2 provider is not supported."));
  }

  private String loadGitHubEmail(OAuth2UserRequest userRequest) {
    try {
      return gitHubEmailClient.findPrimaryVerifiedEmail(userRequest.getAccessToken().getTokenValue())
          .orElseThrow(() -> authenticationException(
              GITHUB_VERIFIED_EMAIL_MISSING_CODE,
              "GitHub account does not expose a verified primary email."));
    } catch (OAuth2AuthenticationException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw authenticationException(
          GITHUB_EMAIL_LOOKUP_FAILED_CODE,
          "GitHub email lookup failed.",
          exception);
    }
  }

  private static OAuth2AuthenticationException authenticationException(String code, String description) {
    return new OAuth2AuthenticationException(new org.springframework.security.oauth2.core.OAuth2Error(
        code, description, null));
  }

  private static OAuth2AuthenticationException authenticationException(
      String code,
      String description,
      Throwable cause
  ) {
    return new OAuth2AuthenticationException(
        new org.springframework.security.oauth2.core.OAuth2Error(code, description, null),
        cause);
  }
}
