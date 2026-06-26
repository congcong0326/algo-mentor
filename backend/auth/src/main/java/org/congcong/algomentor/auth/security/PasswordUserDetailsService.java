package org.congcong.algomentor.auth.security;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.congcong.algomentor.auth.model.AuthRole;
import org.congcong.algomentor.auth.model.AuthUser;
import org.congcong.algomentor.auth.model.AuthUserStatus;
import org.congcong.algomentor.auth.model.PasswordCredential;
import org.congcong.algomentor.auth.repository.AuthUserRepository;
import org.congcong.algomentor.auth.service.AdminEmailRoleService;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.transaction.annotation.Transactional;

public class PasswordUserDetailsService implements UserDetailsService {

  private final AuthUserRepository repository;
  private final Clock clock;
  private final AdminEmailRoleService adminEmailRoleService;

  public PasswordUserDetailsService(
      AuthUserRepository repository,
      Clock clock,
      AdminEmailRoleService adminEmailRoleService
  ) {
    this.repository = repository;
    this.clock = clock;
    this.adminEmailRoleService = adminEmailRoleService;
  }

  @Override
  @Transactional
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    String emailNormalized = normalizeEmail(username);
    PasswordCredential credential = repository.findPasswordCredentialByEmailNormalized(emailNormalized)
        .orElseThrow(() -> new UsernameNotFoundException("Bad credentials."));
    AuthUser user = repository.findUserById(credential.userId())
        .orElseThrow(() -> new UsernameNotFoundException("Bad credentials."));
    if (user.status() == AuthUserStatus.DISABLED) {
      throw new DisabledException("Bad credentials.");
    }
    if (adminEmailRoleService != null) {
      adminEmailRoleService.ensureAdminRole(user.id(), user.email());
    }
    AuthUser updatedUser = repository.updateLastLoginAt(user.id(), Instant.now(clock));
    List<AuthRole> roles = repository.findRoles(updatedUser.id());
    List<AuthRole> effectiveRoles = roles.isEmpty() ? List.of(AuthRole.USER) : roles;
    AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(
        updatedUser.id(),
        updatedUser.email(),
        updatedUser.displayName(),
        updatedUser.avatarUrl(),
        effectiveRoles,
        updatedUser.status());
    return new AuthenticatedUserDetails(
        principal,
        credential.passwordHash(),
        AuthAuthorities.fromRoles(effectiveRoles));
  }

  private static String normalizeEmail(String email) {
    return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
  }
}
