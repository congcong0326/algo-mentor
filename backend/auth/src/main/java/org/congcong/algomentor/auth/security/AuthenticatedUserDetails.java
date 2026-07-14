package org.congcong.algomentor.auth.security;

import java.util.Collection;
import org.congcong.algomentor.auth.model.PasswordCredential;
import org.congcong.algomentor.identity.model.AuthUserStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class AuthenticatedUserDetails implements UserDetails {

  private final AuthenticatedUserPrincipal principal;
  private final PasswordCredential credential;
  private final Collection<? extends GrantedAuthority> authorities;

  public AuthenticatedUserDetails(
      AuthenticatedUserPrincipal principal,
      String passwordHash,
      Collection<? extends GrantedAuthority> authorities
  ) {
    this(
        principal,
        new PasswordCredential(null, principal.userId(), passwordHash, null, null),
        authorities);
  }

  public AuthenticatedUserDetails(
      AuthenticatedUserPrincipal principal,
      PasswordCredential credential,
      Collection<? extends GrantedAuthority> authorities
  ) {
    this.principal = principal;
    this.credential = credential;
    this.authorities = authorities;
  }

  public AuthenticatedUserPrincipal principal() {
    return principal;
  }

  public PasswordCredential credential() {
    return credential;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return authorities;
  }

  @Override
  public String getPassword() {
    return credential.passwordHash();
  }

  @Override
  public String getUsername() {
    return principal.email();
  }

  @Override
  public boolean isEnabled() {
    return principal.status() == AuthUserStatus.ACTIVE;
  }
}
