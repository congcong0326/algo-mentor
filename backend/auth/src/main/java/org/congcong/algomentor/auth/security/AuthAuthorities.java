package org.congcong.algomentor.auth.security;

import java.util.Collection;
import java.util.List;
import org.congcong.algomentor.auth.model.AuthRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * 本地角色到 Spring Security authority 的映射规则。
 */
public final class AuthAuthorities {

  public static final String ROLE_PREFIX = "ROLE_";

  private AuthAuthorities() {
  }

  public static Collection<? extends GrantedAuthority> fromRoles(List<AuthRole> roles) {
    return roles.stream()
        .map(AuthAuthorities::fromRole)
        .toList();
  }

  public static GrantedAuthority fromRole(AuthRole role) {
    return new SimpleGrantedAuthority(ROLE_PREFIX + role.name());
  }
}
