package org.congcong.algomentor.identity.controller.model;

import java.time.Instant;
import java.util.List;
import org.congcong.algomentor.identity.model.AuthRole;
import org.congcong.algomentor.identity.model.AuthUserStatus;

public record AdminUserDetailResponse(
    Long id,
    String email,
    String emailNormalized,
    String displayName,
    String avatarUrl,
    AuthUserStatus status,
    List<AuthRole> roles,
    Instant createdAt,
    Instant updatedAt,
    Instant lastLoginAt,
    Instant deletedAt,
    Long deletedBy
) {

  public AdminUserDetailResponse {
    roles = roles == null ? List.of() : List.copyOf(roles);
  }
}
