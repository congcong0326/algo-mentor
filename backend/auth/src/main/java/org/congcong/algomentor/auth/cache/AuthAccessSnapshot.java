package org.congcong.algomentor.auth.cache;

import java.util.List;
import java.util.Objects;
import org.congcong.algomentor.identity.model.AuthRole;
import org.congcong.algomentor.identity.model.AuthUserStatus;

/** 已认证请求继续放行所需的最小身份快照。 */
public record AuthAccessSnapshot(long userId, String email, AuthUserStatus status, List<AuthRole> roles) {

  public AuthAccessSnapshot {
    if (userId < 1) {
      throw new IllegalArgumentException("userId must be positive");
    }
    email = Objects.requireNonNull(email, "email must not be null");
    status = Objects.requireNonNull(status, "status must not be null");
    roles = List.copyOf(Objects.requireNonNull(roles, "roles must not be null"));
  }
}
