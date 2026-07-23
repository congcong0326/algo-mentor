package org.congcong.algomentor.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import org.congcong.algomentor.auth.config.AuthSecurityPaths;
import org.congcong.algomentor.auth.session.policy.AuthSessionPolicyErrorCode;
import org.congcong.algomentor.auth.session.policy.AuthSessionPolicyException;
import org.congcong.algomentor.auth.session.policy.AuthSessionPolicyLoginService;
import org.congcong.algomentor.identity.model.AuthRole;
import org.congcong.algomentor.identity.model.AuthUserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class OAuth2AuthenticationSuccessHandlerTest {

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void appliesSameSessionPolicyBeforeOAuth2Redirect() throws Exception {
    AuthSessionPolicyLoginService policyLoginService = mock(AuthSessionPolicyLoginService.class);
    MockHttpServletRequest request = request();
    MockHttpServletResponse response = new MockHttpServletResponse();

    new OAuth2AuthenticationSuccessHandler("/", policyLoginService)
        .onAuthenticationSuccess(request, response, authentication());

    verify(policyLoginService).apply(eq(42L), eq(request.getSession(false)));
    assertThat(response.getRedirectedUrl()).isEqualTo("/");
  }

  @Test
  void rejectsOAuth2LoginWhenSessionPolicyCannotBeApplied() throws Exception {
    AuthSessionPolicyLoginService policyLoginService = mock(AuthSessionPolicyLoginService.class);
    MockHttpServletRequest request = request();
    MockHttpSession session = (MockHttpSession) request.getSession(false);
    doThrow(new AuthSessionPolicyException(
        AuthSessionPolicyErrorCode.AUTH_SESSION_POLICY_UNAVAILABLE,
        "unavailable"))
        .when(policyLoginService)
        .apply(42L, session);
    SecurityContextHolder.getContext().setAuthentication(authentication());
    MockHttpServletResponse response = new MockHttpServletResponse();

    new OAuth2AuthenticationSuccessHandler("/", policyLoginService)
        .onAuthenticationSuccess(request, response, authentication());

    assertThat(response.getRedirectedUrl()).isEqualTo(AuthSecurityPaths.OAUTH2_SESSION_POLICY_FAILURE_URL);
    assertThat(session.isInvalid()).isTrue();
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  private static MockHttpServletRequest request() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login/oauth2/code/google");
    request.setSession(new MockHttpSession(null, "oauth-session"));
    return request;
  }

  private static TestingAuthenticationToken authentication() {
    AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(
        42L,
        "member@example.com",
        "Member",
        null,
        List.of(AuthRole.USER),
        AuthUserStatus.ACTIVE);
    AuthenticatedOAuth2User user = new AuthenticatedOAuth2User(
        principal,
        Map.of("sub", "google-sub"),
        AuthAuthorities.fromRoles(principal.roles()));
    TestingAuthenticationToken authentication = new TestingAuthenticationToken(
        user,
        null,
        AuthAuthorities.fromRoles(principal.roles()));
    authentication.setAuthenticated(true);
    return authentication;
  }
}
