package org.congcong.algomentor.auth.security;

import java.io.Serializable;
import java.util.List;
import org.congcong.algomentor.auth.model.AuthRole;
import org.congcong.algomentor.auth.model.AuthUserStatus;
import org.springframework.security.core.AuthenticatedPrincipal;

public record AuthenticatedUserPrincipal(
    Long userId,
    String email,
    String displayName,
    String avatarUrl,
    List<AuthRole> roles,
    AuthUserStatus status
) implements AuthenticatedPrincipal, Serializable {

  private static final long serialVersionUID = 1L;

  public AuthenticatedUserPrincipal {
    if (userId == null || userId < 1) {
      throw new IllegalArgumentException("userId must be a positive number.");
    }
    roles = roles == null ? List.of() : List.copyOf(roles);
    status = status == null ? AuthUserStatus.ACTIVE : status;
  }

  @Override
  public String getName() {
    return userId.toString();
  }
}
