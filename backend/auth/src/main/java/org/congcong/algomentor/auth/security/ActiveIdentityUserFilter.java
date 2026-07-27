package org.congcong.algomentor.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Optional;
import org.congcong.algomentor.auth.betaaccess.service.BetaAccessPolicy;
import org.congcong.algomentor.auth.betaaccess.service.BetaAccessErrorCode;
import org.congcong.algomentor.auth.cache.AuthAccessSnapshot;
import org.congcong.algomentor.auth.cache.AuthAccessSnapshotCache;
import org.congcong.algomentor.auth.config.AuthSecurityPaths;
import org.congcong.algomentor.common.api.ApiErrorLocales;
import org.congcong.algomentor.common.api.ApiErrorMessageResolver;
import org.congcong.algomentor.common.api.ApiErrorResponseFactory;
import org.congcong.algomentor.identity.model.AuthRole;
import org.congcong.algomentor.identity.model.AuthUserStatus;
import org.congcong.algomentor.identity.repository.IdentityUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

public class ActiveIdentityUserFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(ActiveIdentityUserFilter.class);

  private final IdentityUserRepository identityUserRepository;
  private final AuthenticationEntryPoint authenticationEntryPoint;
  private final BetaAccessPolicy betaAccessPolicy;
  private final ObjectMapper objectMapper;
  private final ApiErrorResponseFactory responseFactory;
  private final AuthAccessSnapshotCache accessSnapshotCache;
  private final RequestMatcher apiRequestMatcher = new AntPathRequestMatcher(AuthSecurityPaths.API_PATTERN);

  public ActiveIdentityUserFilter(
      IdentityUserRepository identityUserRepository,
      AuthenticationEntryPoint authenticationEntryPoint
  ) {
    this(
        identityUserRepository,
        authenticationEntryPoint,
        null,
        new ObjectMapper().findAndRegisterModules(),
        new ApiErrorResponseFactory(new ApiErrorMessageResolver()),
        null);
  }

  public ActiveIdentityUserFilter(
      IdentityUserRepository identityUserRepository,
      AuthenticationEntryPoint authenticationEntryPoint,
      BetaAccessPolicy betaAccessPolicy,
      ObjectMapper objectMapper,
      ApiErrorResponseFactory responseFactory
  ) {
    this(
        identityUserRepository,
        authenticationEntryPoint,
        betaAccessPolicy,
        objectMapper,
        responseFactory,
        null);
  }

  public ActiveIdentityUserFilter(
      IdentityUserRepository identityUserRepository,
      AuthenticationEntryPoint authenticationEntryPoint,
      BetaAccessPolicy betaAccessPolicy,
      ObjectMapper objectMapper,
      ApiErrorResponseFactory responseFactory,
      AuthAccessSnapshotCache accessSnapshotCache
  ) {
    this.identityUserRepository = identityUserRepository;
    this.authenticationEntryPoint = authenticationEntryPoint;
    this.betaAccessPolicy = betaAccessPolicy;
    this.objectMapper = objectMapper;
    this.responseFactory = responseFactory;
    this.accessSnapshotCache = accessSnapshotCache;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain
  ) throws ServletException, IOException {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    Optional<AuthenticatedUserPrincipal> principal = authenticatedPrincipal(authentication);
    if (!apiRequestMatcher.matches(request)
        || authentication == null
        || !authentication.isAuthenticated()
        || principal.isEmpty()) {
      filterChain.doFilter(request, response);
      return;
    }

    IdentityValidation validation = validate(principal.get());
    if (validation == IdentityValidation.ALLOWED) {
      AuthenticatedUserResponseHeaders.write(response, principal.get());
      filterChain.doFilter(request, response);
      return;
    }

    SecurityContextHolder.clearContext();
    HttpSession session = request.getSession(false);
    if (session != null) {
      session.invalidate();
    }
    if (validation == IdentityValidation.BETA_ACCESS_DENIED) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      response.setContentType("application/json");
      response.setCharacterEncoding("UTF-8");
      objectMapper.writeValue(response.getWriter(), responseFactory.failure(
          BetaAccessErrorCode.AUTH_BETA_ACCESS_DENIED.name(),
          "当前邮箱不在内测准入名单中。",
          ApiErrorLocales.parse(request.getHeader("Accept-Language"))));
      return;
    }
    authenticationEntryPoint.commence(
        request,
        response,
        new InsufficientAuthenticationException("Identity user is not active."));
  }

  private IdentityValidation validate(AuthenticatedUserPrincipal principal) {
    try {
      Optional<AuthAccessSnapshot> snapshot = accessSnapshotCache == null
          ? loadSnapshot(principal.userId())
          : accessSnapshotCache.get(principal.userId(), () -> loadSnapshot(principal.userId()));
      if (snapshot.isEmpty() || snapshot.get().status() != AuthUserStatus.ACTIVE) {
        return IdentityValidation.INACTIVE;
      }
      if (betaAccessPolicy == null) {
        return IdentityValidation.ALLOWED;
      }
      return betaAccessPolicy.evaluate(
          snapshot.get().email(), snapshot.get().roles().contains(AuthRole.ADMIN)).allowed()
          ? IdentityValidation.ALLOWED
          : IdentityValidation.BETA_ACCESS_DENIED;
    } catch (RuntimeException exception) {
      log.warn("Failed to validate active identity user for authenticated request. userId={}",
          principal.userId(),
          exception);
      return IdentityValidation.INACTIVE;
    }
  }

  private Optional<AuthAccessSnapshot> loadSnapshot(long userId) {
    return identityUserRepository.findUserById(userId)
        .map(user -> new AuthAccessSnapshot(user.id(), user.email(), user.status(),
            identityUserRepository.findRoles(userId)));
  }

  private Optional<AuthenticatedUserPrincipal> authenticatedPrincipal(Authentication authentication) {
    if (authentication == null) {
      return Optional.empty();
    }
    if (authentication.getPrincipal() instanceof AuthenticatedUserPrincipal principal) {
      return Optional.of(principal);
    }
    if (authentication.getPrincipal() instanceof AuthenticatedOAuth2User oauth2User) {
      return Optional.of(oauth2User.authenticatedUserPrincipal());
    }
    return Optional.empty();
  }

  private enum IdentityValidation {
    ALLOWED,
    INACTIVE,
    BETA_ACCESS_DENIED
  }
}
