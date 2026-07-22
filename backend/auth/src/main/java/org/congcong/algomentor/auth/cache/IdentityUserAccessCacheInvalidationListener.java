package org.congcong.algomentor.auth.cache;

import java.util.Objects;
import org.congcong.algomentor.identity.event.IdentityUserStatusChangedEvent;
import org.springframework.context.event.EventListener;

/** 用户状态变更提交时失效对应认证访问快照。 */
public final class IdentityUserAccessCacheInvalidationListener {

  private final AuthAccessSnapshotCache cache;

  public IdentityUserAccessCacheInvalidationListener(AuthAccessSnapshotCache cache) {
    this.cache = Objects.requireNonNull(cache, "cache must not be null");
  }

  @EventListener
  public void onStatusChanged(IdentityUserStatusChangedEvent event) {
    cache.invalidate(event.userId());
  }
}
