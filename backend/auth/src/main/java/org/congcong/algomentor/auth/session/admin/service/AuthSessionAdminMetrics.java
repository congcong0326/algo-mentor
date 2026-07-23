package org.congcong.algomentor.auth.session.admin.service;

import java.time.Duration;

public interface AuthSessionAdminMetrics {

  void recordQuery(Duration duration);

  void recordRevocation(AuthSessionAdminRevocationOutcome outcome);
}
