package org.congcong.algomentor.auth.security;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

public class AuthenticatedOidcUser extends AuthenticatedOAuth2User implements OidcUser {

  private static final long serialVersionUID = 1L;

  private final OidcUser delegate;
  private final List<GrantedAuthority> authorities;

  public AuthenticatedOidcUser(
      AuthenticatedUserPrincipal authenticatedUserPrincipal,
      OidcUser delegate,
      Collection<? extends GrantedAuthority> authorities
  ) {
    super(authenticatedUserPrincipal, delegate.getAttributes(), authorities);
    this.delegate = delegate;
    this.authorities = List.copyOf(authorities);
  }

  @Override
  public Map<String, Object> getClaims() {
    return delegate.getClaims();
  }

  @Override
  public OidcUserInfo getUserInfo() {
    return delegate.getUserInfo();
  }

  @Override
  public OidcIdToken getIdToken() {
    return delegate.getIdToken();
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return authorities;
  }
}
