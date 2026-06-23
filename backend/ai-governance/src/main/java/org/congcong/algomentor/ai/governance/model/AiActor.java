package org.congcong.algomentor.ai.governance.model;

import java.util.Set;
import org.congcong.algomentor.auth.model.AuthRole;

public record AiActor(Long userId, Set<AuthRole> roles, boolean authenticated) {

  public AiActor {
    if (authenticated && (userId == null || userId < 1)) {
      throw new IllegalArgumentException("Authenticated AI actor must have a positive user id");
    }
    roles = roles == null ? Set.of() : Set.copyOf(roles);
  }

  public static AiActor anonymous() {
    return new AiActor(null, Set.of(), false);
  }

  public boolean admin() {
    return roles.contains(AuthRole.ADMIN);
  }
}
