package org.congcong.algomentor.auth.session;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.congcong.algomentor.identity.model.AuthUserStatus;

public class MicrometerAuthSessionMetrics implements AuthSessionMetrics {

  static final String SESSION_REVOCATIONS_TOTAL = "algo_mentor_auth_session_revocations_total";
  static final String SESSION_REVOCATION_FAILURES_TOTAL = "algo_mentor_auth_session_revocation_failures_total";
  private static final String STATUS_TAG = "status";

  private final MeterRegistry registry;

  public MicrometerAuthSessionMetrics(MeterRegistry registry) {
    this.registry = registry;
  }

  @Override
  public void recordSuccess(AuthUserStatus status, int revokedSessions) {
    if (revokedSessions <= 0) {
      return;
    }
    Counter.builder(SESSION_REVOCATIONS_TOTAL)
        .tag(STATUS_TAG, status.name())
        .register(registry)
        .increment(revokedSessions);
  }

  @Override
  public void recordFailure(AuthUserStatus status) {
    Counter.builder(SESSION_REVOCATION_FAILURES_TOTAL)
        .tag(STATUS_TAG, status.name())
        .register(registry)
        .increment();
  }
}
