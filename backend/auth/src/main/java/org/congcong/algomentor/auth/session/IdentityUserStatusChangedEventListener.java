package org.congcong.algomentor.auth.session;

import org.congcong.algomentor.identity.event.IdentityUserStatusChangedEvent;
import org.congcong.algomentor.identity.model.AuthUserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;

public class IdentityUserStatusChangedEventListener {

  private static final Logger log = LoggerFactory.getLogger(IdentityUserStatusChangedEventListener.class);

  private final AuthSessionRevocationService revocationService;
  private final AuthSessionMetrics metrics;

  public IdentityUserStatusChangedEventListener(
      AuthSessionRevocationService revocationService,
      AuthSessionMetrics metrics
  ) {
    this.revocationService = revocationService;
    this.metrics = metrics;
  }

  @EventListener
  public void onStatusChanged(IdentityUserStatusChangedEvent event) {
    if (event.currentStatus() != AuthUserStatus.DISABLED && event.currentStatus() != AuthUserStatus.DELETED) {
      return;
    }
    try {
      int revoked = revocationService.revokeSessionsForUser(event.userId());
      metrics.recordSuccess(event.currentStatus(), revoked);
    } catch (RuntimeException exception) {
      metrics.recordFailure(event.currentStatus());
      log.error("Failed to revoke sessions for identity status change. userId={} currentStatus={}",
          event.userId(),
          event.currentStatus(),
          exception);
    }
  }
}
