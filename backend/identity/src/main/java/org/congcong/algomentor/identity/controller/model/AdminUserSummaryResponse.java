package org.congcong.algomentor.identity.controller.model;

import java.time.Instant;
import java.util.List;
import org.congcong.algomentor.identity.model.AuthRole;
import org.congcong.algomentor.identity.model.AuthUserStatus;

public record AdminUserSummaryResponse(
    Long id,
    String email,
    String displayName,
    String avatarUrl,
    AuthUserStatus status,
    List<AuthRole> roles,
    Instant createdAt,
    Instant updatedAt,
    Instant lastLoginAt
) {

  public AdminUserSummaryResponse {
    roles = roles == null ? List.of() : List.copyOf(roles);
  }
}
