package org.congcong.algomentor.api.service;

import java.util.Set;
import org.congcong.algomentor.ai.governance.model.AiActor;
import org.congcong.algomentor.auth.security.CurrentUserIdProvider;
import org.springframework.stereotype.Service;

@Service
public class AiActorResolver {

  private final CurrentUserIdProvider currentUserIdProvider;

  public AiActorResolver(CurrentUserIdProvider currentUserIdProvider) {
    this.currentUserIdProvider = currentUserIdProvider;
  }

  public AiActor currentActor() {
    return currentUserIdProvider.currentUser()
        .map(user -> new AiActor(user.userId(), Set.copyOf(user.roles()), true))
        .orElseGet(AiActor::anonymous);
  }
}
