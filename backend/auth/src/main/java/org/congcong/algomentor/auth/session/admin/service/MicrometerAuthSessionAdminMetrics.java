package org.congcong.algomentor.auth.session.admin.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;

public class MicrometerAuthSessionAdminMetrics implements AuthSessionAdminMetrics {

  static final String QUERY_SECONDS = "algo_mentor_auth_session_admin_query_seconds";
  static final String REVOCATIONS_TOTAL = "algo_mentor_auth_session_admin_revocations_total";
  private static final String OUTCOME_TAG = "outcome";

  private final MeterRegistry registry;
  private final Timer queryTimer;

  public MicrometerAuthSessionAdminMetrics(MeterRegistry registry) {
    this.registry = registry;
    this.queryTimer = Timer.builder(QUERY_SECONDS).register(registry);
  }

  @Override
  public void recordQuery(Duration duration) {
    queryTimer.record(duration);
  }

  @Override
  public void recordRevocation(AuthSessionAdminRevocationOutcome outcome) {
    Counter.builder(REVOCATIONS_TOTAL)
        .tag(OUTCOME_TAG, outcome.metricTag())
        .register(registry)
        .increment();
  }
}
