package org.congcong.algomentor.auth.security;

import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class AuthenticatedUserDetails implements UserDetails {

  private final AuthenticatedUserPrincipal principal;
  private final String passwordHash;
  private final Collection<? extends GrantedAuthority> authorities;

  public AuthenticatedUserDetails(
      AuthenticatedUserPrincipal principal,
      String passwordHash,
      Collection<? extends GrantedAuthority> authorities
  ) {
    this.principal = principal;
    this.passwordHash = passwordHash;
    this.authorities = authorities;
  }

  public AuthenticatedUserPrincipal principal() {
    return principal;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return authorities;
  }

  @Override
  public String getPassword() {
    return passwordHash;
  }

  @Override
  public String getUsername() {
    return principal.email();
  }

  @Override
  public boolean isEnabled() {
    return principal.status() == org.congcong.algomentor.auth.model.AuthUserStatus.ACTIVE;
  }
}
