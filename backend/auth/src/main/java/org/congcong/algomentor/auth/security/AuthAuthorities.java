package org.congcong.algomentor.auth.security;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import org.congcong.algomentor.auth.service.AuthPermissionService;
import org.congcong.algomentor.identity.model.AuthRole;
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
    LinkedHashSet<GrantedAuthority> authorities = new LinkedHashSet<>();
    if (roles != null) {
      roles.stream().map(AuthAuthorities::fromRole).forEach(authorities::add);
      new AuthPermissionService().permissionsFor(roles).stream()
          .map(SimpleGrantedAuthority::new)
          .forEach(authorities::add);
    }
    return List.copyOf(authorities);
  }

  public static GrantedAuthority fromRole(AuthRole role) {
    return new SimpleGrantedAuthority(ROLE_PREFIX + role.name());
  }
}
