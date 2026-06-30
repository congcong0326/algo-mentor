package org.congcong.algomentor.auth.model;

import java.util.List;
import org.congcong.algomentor.identity.model.AuthRole;
import org.congcong.algomentor.identity.model.AuthUserStatus;

public record CurrentUserResponse(
    Long id,
    String email,
    String displayName,
    String avatarUrl,
    List<AuthRole> roles,
    List<String> permissions,
    AuthUserStatus status
) {

  public CurrentUserResponse {
    if (id == null || id < 1) {
      throw new IllegalArgumentException("id must be a positive number.");
    }
    roles = roles == null ? List.of() : List.copyOf(roles);
    permissions = permissions == null ? List.of() : List.copyOf(permissions);
  }
}
