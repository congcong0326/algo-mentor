package org.congcong.algomentor.auth.betaaccess.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

public class MicrometerBetaAccessMetrics implements BetaAccessMetrics {

  static final String SESSION_REVOCATION_FAILURES_TOTAL =
      "algo_mentor_beta_access_session_revocation_failures_total";

  private final Counter counter;

  public MicrometerBetaAccessMetrics(MeterRegistry meterRegistry) {
    this.counter = Counter.builder(SESSION_REVOCATION_FAILURES_TOTAL).register(meterRegistry);
  }

  @Override
  public void recordSessionRevocationFailure() {
    counter.increment();
  }
}
