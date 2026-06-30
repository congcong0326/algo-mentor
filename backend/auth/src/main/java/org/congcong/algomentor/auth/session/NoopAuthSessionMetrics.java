package org.congcong.algomentor.auth.session;

import org.congcong.algomentor.identity.model.AuthUserStatus;

public class NoopAuthSessionMetrics implements AuthSessionMetrics {

  @Override
  public void recordSuccess(AuthUserStatus status, int revokedSessions) {
  }

  @Override
  public void recordFailure(AuthUserStatus status) {
  }
}
