package org.congcong.algomentor.auth.security;

import java.time.Clock;
import java.time.Instant;
import org.congcong.algomentor.auth.betaaccess.service.BetaAccessException;
import org.congcong.algomentor.auth.betaaccess.service.BetaAccessPolicy;
import org.congcong.algomentor.auth.model.PasswordCredential;
import org.congcong.algomentor.auth.passwordreset.PasswordResetErrorCode;
import org.congcong.algomentor.auth.repository.AuthUserRepository;
import org.congcong.algomentor.identity.model.AuthRole;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

public class AuthenticatedDaoAuthenticationProvider extends DaoAuthenticationProvider {

  private final BetaAccessPolicy betaAccessPolicy;
  private final AuthUserRepository authUserRepository;
  private final Clock clock;

  public AuthenticatedDaoAuthenticationProvider(
      PasswordEncoder passwordEncoder,
      PasswordUserDetailsService userDetailsService
  ) {
    this(passwordEncoder, userDetailsService, null, null, null);
  }

  public AuthenticatedDaoAuthenticationProvider(
      PasswordEncoder passwordEncoder,
      PasswordUserDetailsService userDetailsService,
      BetaAccessPolicy betaAccessPolicy
  ) {
    this(passwordEncoder, userDetailsService, null, null, betaAccessPolicy);
  }

  public AuthenticatedDaoAuthenticationProvider(
      PasswordEncoder passwordEncoder,
      PasswordUserDetailsService userDetailsService,
      AuthUserRepository authUserRepository,
      Clock clock,
      BetaAccessPolicy betaAccessPolicy
  ) {
    super(passwordEncoder);
    setUserDetailsService(userDetailsService);
    this.betaAccessPolicy = betaAccessPolicy;
    this.authUserRepository = authUserRepository;
    this.clock = clock;
  }

  @Override
  protected Authentication createSuccessAuthentication(
      Object principal,
      Authentication authentication,
      UserDetails user
  ) {
    if (user instanceof AuthenticatedUserDetails authenticatedUserDetails) {
      requireBetaAccess(authenticatedUserDetails.principal());
      AuthenticatedUserPrincipal authenticatedPrincipal = consumeTemporaryPassword(authenticatedUserDetails);
      return UsernamePasswordAuthenticationToken.authenticated(
          authenticatedPrincipal,
          authentication.getCredentials(),
          authenticatedUserDetails.getAuthorities());
    }
    return super.createSuccessAuthentication(principal, authentication, user);
  }

  private AuthenticatedUserPrincipal consumeTemporaryPassword(AuthenticatedUserDetails userDetails) {
    PasswordCredential credential = userDetails.credential();
    if (!credential.resetRequired()) {
      return userDetails.principal().withPasswordChangeRequired(false);
    }
    Instant now = clock == null ? Instant.now() : Instant.now(clock);
    if (credential.temporaryPasswordExpiresAt() == null
        || !credential.temporaryPasswordExpiresAt().isAfter(now)) {
      throw new TemporaryPasswordAuthenticationException(
          PasswordResetErrorCode.AUTH_TEMPORARY_PASSWORD_EXPIRED,
          "临时密码已过期，请联系管理员重新重置。");
    }
    if (credential.temporaryPasswordConsumedAt() != null) {
      throw new TemporaryPasswordAuthenticationException(
          PasswordResetErrorCode.AUTH_TEMPORARY_PASSWORD_CONSUMED,
          "临时密码已使用，请联系管理员重新重置。");
    }
    if (authUserRepository == null
        || !authUserRepository.consumeTemporaryPassword(
            credential.userId(),
            credential.passwordHash(),
            now)) {
      PasswordCredential current = authUserRepository == null
          ? credential
          : authUserRepository.findPasswordCredentialByUserId(credential.userId()).orElse(credential);
      PasswordResetErrorCode code = current.temporaryPasswordExpiresAt() == null
          || !current.temporaryPasswordExpiresAt().isAfter(now)
          ? PasswordResetErrorCode.AUTH_TEMPORARY_PASSWORD_EXPIRED
          : PasswordResetErrorCode.AUTH_TEMPORARY_PASSWORD_CONSUMED;
      throw new TemporaryPasswordAuthenticationException(
          code,
          code == PasswordResetErrorCode.AUTH_TEMPORARY_PASSWORD_EXPIRED
              ? "临时密码已过期，请联系管理员重新重置。"
              : "临时密码已使用，请联系管理员重新重置。");
    }
    return userDetails.principal().withPasswordChangeRequired(true);
  }

  private void requireBetaAccess(AuthenticatedUserPrincipal principal) {
    if (betaAccessPolicy == null) {
      return;
    }
    try {
      betaAccessPolicy.requireAllowed(principal.email(), principal.roles().contains(AuthRole.ADMIN));
    } catch (BetaAccessException exception) {
      throw new BetaAccessAuthenticationException(exception.code(), exception.getMessage());
    }
  }
}
