package org.congcong.algomentor.auth.model;

import java.util.List;
import org.congcong.algomentor.auth.security.AuthSessionAuthenticationMethod;
import org.congcong.algomentor.identity.model.AuthRole;
import org.congcong.algomentor.identity.model.AuthUserStatus;

public record CurrentUserResponse(
    Long id,
    String email,
    String displayName,
    String avatarUrl,
    List<AuthRole> roles,
    List<String> permissions,
    AuthUserStatus status,
    boolean passwordChangeRequired,
    boolean passwordConfigured,
    AuthSessionAuthenticationMethod sessionAuthenticationMethod,
    boolean passwordLoginEnabled
) {

  public CurrentUserResponse(
      Long id,
      String email,
      String displayName,
      String avatarUrl,
      List<AuthRole> roles,
      List<String> permissions,
      AuthUserStatus status
  ) {
    this(id, email, displayName, avatarUrl, roles, permissions, status, false, false, null, true);
  }

  public CurrentUserResponse(
      Long id,
      String email,
      String displayName,
      String avatarUrl,
      List<AuthRole> roles,
      List<String> permissions,
      AuthUserStatus status,
      boolean passwordChangeRequired
  ) {
    this(id, email, displayName, avatarUrl, roles, permissions, status, passwordChangeRequired, false, null, true);
  }

  public CurrentUserResponse(
      Long id,
      String email,
      String displayName,
      String avatarUrl,
      List<AuthRole> roles,
      List<String> permissions,
      AuthUserStatus status,
      boolean passwordChangeRequired,
      boolean passwordConfigured,
      AuthSessionAuthenticationMethod sessionAuthenticationMethod
  ) {
    this(
        id,
        email,
        displayName,
        avatarUrl,
        roles,
        permissions,
        status,
        passwordChangeRequired,
        passwordConfigured,
        sessionAuthenticationMethod,
        true);
  }

  public CurrentUserResponse {
    if (id == null || id < 1) {
      throw new IllegalArgumentException("id must be a positive number.");
    }
    roles = roles == null ? List.of() : List.copyOf(roles);
    permissions = permissions == null ? List.of() : List.copyOf(permissions);
  }
}
