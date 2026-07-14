package org.congcong.algomentor.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.congcong.algomentor.auth.betaaccess.model.BetaAccessSettings;
import org.congcong.algomentor.auth.betaaccess.repository.BetaAccessRepository;
import org.congcong.algomentor.auth.betaaccess.service.BetaAccessPolicy;
import org.congcong.algomentor.common.api.ApiErrorMessageResolver;
import org.congcong.algomentor.common.api.ApiErrorResponseFactory;
import org.congcong.algomentor.identity.model.AuthRole;
import org.congcong.algomentor.identity.model.AuthUser;
import org.congcong.algomentor.identity.model.AuthUserStatus;
import org.congcong.algomentor.identity.repository.IdentityUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;

class ActiveIdentityUserFilterBetaAccessTest {

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void rechecksDatabaseEmailAndRoleThenInvalidatesDeniedSession() throws Exception {
    IdentityUserRepository identityRepository = mock(IdentityUserRepository.class);
    BetaAccessRepository betaRepository = mock(BetaAccessRepository.class);
    when(identityRepository.findUserById(42L)).thenReturn(Optional.of(user()));
    when(identityRepository.findRoles(42L)).thenReturn(List.of(AuthRole.USER));
    when(betaRepository.findSettings()).thenReturn(Optional.of(new BetaAccessSettings(
        (short) 1,
        true,
        1L,
        "Admin",
        Instant.EPOCH)));
    when(betaRepository.isAllowedEmail("member@example.com")).thenReturn(false);
    ActiveIdentityUserFilter filter = new ActiveIdentityUserFilter(
        identityRepository,
        mock(AuthenticationEntryPoint.class),
        new BetaAccessPolicy(betaRepository, null),
        new ObjectMapper().findAndRegisterModules(),
        new ApiErrorResponseFactory(new ApiErrorMessageResolver()));
    AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(
        42L,
        "stale-session-email@example.com",
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
    request.setServletPath("/api/auth/me");
    request.getSession(true);
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicBoolean continued = new AtomicBoolean();

    filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> continued.set(true));

    assertThat(continued).isFalse();
    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getContentAsString()).contains("AUTH_BETA_ACCESS_DENIED");
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  private static AuthUser user() {
    return new AuthUser(
        42L,
        "member@example.com",
        "member@example.com",
        "Member",
        null,
        AuthUserStatus.ACTIVE,
        Instant.EPOCH,
        Instant.EPOCH,
        null,
        null,
        null);
  }
}
