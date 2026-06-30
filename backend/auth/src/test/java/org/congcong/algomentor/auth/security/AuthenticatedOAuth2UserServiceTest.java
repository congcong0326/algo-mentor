package org.congcong.algomentor.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.congcong.algomentor.auth.service.OAuth2LoginUserService;
import org.congcong.algomentor.auth.service.OAuth2LoginUserServiceTest.InMemoryAuthUserRepository;
import org.congcong.algomentor.identity.model.AuthRole;
import org.congcong.algomentor.identity.model.AuthUserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

class AuthenticatedOAuth2UserServiceTest {

  private static final Instant NOW = Instant.parse("2026-06-22T07:00:00Z");

  private final InMemoryAuthUserRepository repository = new InMemoryAuthUserRepository();
  private final OAuth2LoginUserService loginUserService = new OAuth2LoginUserService(
      repository,
      repository,
      Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void oauth2UserServiceWrapsGoogleUserWithAuthenticatedPrincipal() {
    AuthenticatedOAuth2UserService service = new AuthenticatedOAuth2UserService(
        loginUserService,
        request -> new DefaultOAuth2User(
            List.of(),
            googleAttributes("google-sub-1", "user@example.com", "User Name", "avatar"),
            "sub"));

    OAuth2User user = service.loadUser(new OAuth2UserRequest(
        googleClientRegistration(),
        accessToken()));

    assertThat(user).isInstanceOf(AuthenticatedOAuth2User.class);
    AuthenticatedUserPrincipal principal = ((AuthenticatedOAuth2User) user).authenticatedUserPrincipal();
    assertThat(principal.userId()).isEqualTo(1L);
    assertThat(principal.email()).isEqualTo("user@example.com");
    assertThat(principal.roles()).containsExactly(AuthRole.USER);
    assertThat(user.getAuthorities())
        .extracting("authority")
        .containsExactly("ROLE_USER");
    assertThat(user.getAttributes()).containsEntry("sub", "google-sub-1");
    assertThat(user.getName()).isEqualTo("1");
  }

  @Test
  void oidcUserServiceWrapsGoogleUserWithAuthenticatedPrincipal() {
    OidcIdToken idToken = new OidcIdToken(
        "id-token",
        NOW.minusSeconds(60),
        NOW.plusSeconds(3600),
        googleAttributes("google-sub-2", "oidc@example.com", "Oidc User", "oidc-avatar"));
    AuthenticatedOidcUserService service = new AuthenticatedOidcUserService(
        loginUserService,
        request -> new DefaultOidcUser(List.of(), idToken));

    OidcUser user = service.loadUser(new OidcUserRequest(
        googleClientRegistration(),
        accessToken(),
        idToken));

    assertThat(user).isInstanceOf(AuthenticatedOidcUser.class);
    AuthenticatedUserPrincipal principal = ((AuthenticatedOidcUser) user).authenticatedUserPrincipal();
    assertThat(principal.userId()).isEqualTo(1L);
    assertThat(principal.email()).isEqualTo("oidc@example.com");
    assertThat(principal.status()).isEqualTo(AuthUserStatus.ACTIVE);
    assertThat(user.getIdToken()).isSameAs(idToken);
    assertThat(user.getClaims()).containsEntry("sub", "google-sub-2");
  }

  private static ClientRegistration googleClientRegistration() {
    return ClientRegistration.withRegistrationId("google")
        .clientId("client-id")
        .clientSecret("client-secret")
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
        .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
        .tokenUri("https://oauth2.googleapis.com/token")
        .userInfoUri("https://openidconnect.googleapis.com/v1/userinfo")
        .userNameAttributeName("sub")
        .clientName("Google")
        .scope("openid", "profile", "email")
        .build();
  }

  private static OAuth2AccessToken accessToken() {
    return new OAuth2AccessToken(
        OAuth2AccessToken.TokenType.BEARER,
        "access-token",
        NOW.minusSeconds(60),
        NOW.plusSeconds(3600));
  }

  private static Map<String, Object> googleAttributes(
      String subject,
      String email,
      String name,
      String picture
  ) {
    Map<String, Object> attributes = new HashMap<>();
    attributes.put("sub", subject);
    attributes.put("email", email);
    attributes.put("name", name);
    attributes.put("picture", picture);
    return attributes;
  }
}
