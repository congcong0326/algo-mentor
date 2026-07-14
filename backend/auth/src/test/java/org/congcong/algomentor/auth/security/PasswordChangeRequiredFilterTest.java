package org.congcong.algomentor.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.congcong.algomentor.common.api.ApiErrorMessageResolver;
import org.congcong.algomentor.common.api.ApiErrorResponseFactory;
import org.congcong.algomentor.identity.model.AuthRole;
import org.congcong.algomentor.identity.model.AuthUserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class PasswordChangeRequiredFilterTest {

  private final PasswordChangeRequiredFilter filter = new PasswordChangeRequiredFilter(
      new ObjectMapper().findAndRegisterModules(),
      new ApiErrorResponseFactory(new ApiErrorMessageResolver()));

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void blocksBusinessApisForRestrictedSession() throws Exception {
    authenticateRestrictedUser();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/learning-plans");
    request.setServletPath("/api/learning-plans");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicBoolean continued = new AtomicBoolean();

    filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> continued.set(true));

    assertThat(continued).isFalse();
    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getContentAsString()).contains("AUTH_PASSWORD_CHANGE_REQUIRED");
  }

  @Test
  void allowsMeCompleteResetAndLogoutOnly() throws Exception {
    for (String methodAndPath : List.of(
        "GET /api/auth/me",
        "POST /api/auth/password/complete-reset",
        "POST /api/auth/logout")) {
      authenticateRestrictedUser();
      String[] parts = methodAndPath.split(" ", 2);
      MockHttpServletRequest request = new MockHttpServletRequest(parts[0], parts[1]);
      request.setServletPath(parts[1]);
      MockHttpServletResponse response = new MockHttpServletResponse();
      AtomicBoolean continued = new AtomicBoolean();

      filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> continued.set(true));

      assertThat(continued).as(methodAndPath).isTrue();
    }
  }

  private static void authenticateRestrictedUser() {
    AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(
        42L,
        "member@example.com",
        "Member",
        null,
        List.of(AuthRole.USER),
        AuthUserStatus.ACTIVE,
        true);
    SecurityContextHolder.getContext().setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(
            principal,
            null,
            AuthAuthorities.fromRoles(principal.roles())));
  }
}
