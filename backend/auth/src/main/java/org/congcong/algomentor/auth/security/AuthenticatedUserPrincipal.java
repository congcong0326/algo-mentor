package org.congcong.algomentor.auth.security;

import java.util.List;
import org.congcong.algomentor.auth.model.AuthRole;
import org.congcong.algomentor.auth.model.AuthUserStatus;

public record AuthenticatedUserPrincipal(
    Long userId,
    String email,
    String displayName,
    String avatarUrl,
    List<AuthRole> roles,
    AuthUserStatus status
) {

  public AuthenticatedUserPrincipal {
    if (userId == null || userId < 1) {
      throw new IllegalArgumentException("userId must be a positive number.");
    }
    roles = roles == null ? List.of() : List.copyOf(roles);
    status = status == null ? AuthUserStatus.ACTIVE : status;
  }
}
