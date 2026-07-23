package org.congcong.algomentor.auth.session.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.congcong.algomentor.auth.config.AuthProperties;
import org.congcong.algomentor.auth.security.ApiAuthenticationEntryPoint;
import org.congcong.algomentor.auth.security.AuthAuthorities;
import org.congcong.algomentor.auth.security.AuthenticatedUserPrincipal;
import org.congcong.algomentor.identity.model.AuthRole;
import org.congcong.algomentor.identity.model.AuthUserStatus;
import org.congcong.algomentor.common.api.ApiErrorMessageResolver;
import org.congcong.algomentor.common.api.ApiErrorResponseFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class AuthSessionAbsoluteTimeoutFilterTest {

  private static final Instant NOW = Instant.parse("2026-07-23T10:00:00Z");

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void allowsHistoricalSessionWithoutAbsoluteTimeoutSnapshot() throws Exception {
    MockHttpServletRequest request = authenticatedRequest();
    AtomicBoolean continued = new AtomicBoolean();

    filter(NOW).doFilter(request, new MockHttpServletResponse(),
        (ignoredRequest, ignoredResponse) -> continued.set(true));

    assertThat(continued).isTrue();
  }

  @Test
  void allowsUnexpiredSessionAndTightensIdleTimeoutToRemainingAbsoluteLifetime() throws Exception {
    MockHttpServletRequest request = authenticatedRequest();
    request.getSession().setAttribute(
        AuthSessionAttributeNames.ABSOLUTE_EXPIRES_AT_EPOCH_MILLIS,
        NOW.plusSeconds(30).toEpochMilli());
    AtomicBoolean continued = new AtomicBoolean();

    filter(NOW).doFilter(request, new MockHttpServletResponse(),
        (ignoredRequest, ignoredResponse) -> continued.set(true));

    assertThat(continued).isTrue();
    assertThat(request.getSession().getMaxInactiveInterval()).isEqualTo(30);
  }

  @Test
  void rejectsSessionAtExactAbsoluteExpiry() throws Exception {
    MockHttpServletRequest request = authenticatedRequest();
    MockHttpSession session = (MockHttpSession) request.getSession();
    session.setAttribute(AuthSessionAttributeNames.ABSOLUTE_EXPIRES_AT_EPOCH_MILLIS, NOW.toEpochMilli());
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicBoolean continued = new AtomicBoolean();

    filter(NOW).doFilter(request, response,
        (ignoredRequest, ignoredResponse) -> continued.set(true));

    assertThat(continued).isFalse();
    assertThat(session.isInvalid()).isTrue();
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentAsString()).contains("AUTH_UNAUTHENTICATED");
  }

  private static AuthSessionAbsoluteTimeoutFilter filter(Instant now) {
    return new AuthSessionAbsoluteTimeoutFilter(
        new ApiAuthenticationEntryPoint(
            new ObjectMapper().findAndRegisterModules(),
            new ApiErrorResponseFactory(new ApiErrorMessageResolver())),
        new NoopAuthSessionPolicyMetrics(),
        Clock.fixed(now, ZoneOffset.UTC),
        new AuthProperties());
  }

  private static MockHttpServletRequest authenticatedRequest() {
    AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(
        42L,
        "member@example.com",
        "Member",
        null,
        List.of(AuthRole.USER),
        AuthUserStatus.ACTIVE);
    SecurityContextHolder.getContext().setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(
            principal,
            null,
            AuthAuthorities.fromRoles(principal.roles())));
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
    request.setSession(new MockHttpSession());
    return request;
  }
}
