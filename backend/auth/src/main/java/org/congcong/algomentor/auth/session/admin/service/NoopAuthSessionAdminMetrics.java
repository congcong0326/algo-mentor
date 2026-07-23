package org.congcong.algomentor.auth.session.admin.service;

import java.time.Duration;

public class NoopAuthSessionAdminMetrics implements AuthSessionAdminMetrics {

  @Override
  public void recordQuery(Duration duration) {
  }

  @Override
  public void recordRevocation(AuthSessionAdminRevocationOutcome outcome) {
  }
}
