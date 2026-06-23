package org.congcong.algomentor.auth.security;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

public class AuthenticatedOAuth2User implements OAuth2User, Serializable {

  private static final long serialVersionUID = 1L;

  private final AuthenticatedUserPrincipal authenticatedUserPrincipal;
  private final Map<String, Object> attributes;
  private final List<GrantedAuthority> authorities;

  public AuthenticatedOAuth2User(
      AuthenticatedUserPrincipal authenticatedUserPrincipal,
      Map<String, Object> attributes,
      Collection<? extends GrantedAuthority> authorities
  ) {
    this.authenticatedUserPrincipal = authenticatedUserPrincipal;
    this.attributes = Map.copyOf(attributes);
    this.authorities = List.copyOf(authorities);
  }

  public AuthenticatedUserPrincipal authenticatedUserPrincipal() {
    return authenticatedUserPrincipal;
  }

  @Override
  public Map<String, Object> getAttributes() {
    return attributes;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return authorities;
  }

  @Override
  public String getName() {
    return authenticatedUserPrincipal.userId().toString();
  }
}
