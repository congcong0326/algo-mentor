package org.congcong.algomentor.auth.session.policy;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/** Micrometer 用户会话策略指标实现。 */
public class MicrometerAuthSessionPolicyMetrics implements AuthSessionPolicyMetrics {

  static final String RESOLUTIONS_TOTAL = "algo_mentor_auth_session_policy_resolutions_total";
  static final String EVICTIONS_TOTAL = "algo_mentor_auth_session_policy_evictions_total";
  static final String ABSOLUTE_EXPIRATIONS_TOTAL = "algo_mentor_auth_session_absolute_expirations_total";
  static final String FAILURES_TOTAL = "algo_mentor_auth_session_policy_failures_total";
  private static final String SOURCE_TAG = "source";
  private static final String OUTCOME_TAG = "outcome";
  private static final String OPERATION_TAG = "operation";
  private static final String SUCCESS_OUTCOME = "success";
  private static final String FAILURE_OUTCOME = "failure";

  private final MeterRegistry registry;

  public MicrometerAuthSessionPolicyMetrics(MeterRegistry registry) {
    this.registry = registry;
  }

  @Override
  public void recordResolution(AuthSessionPolicyResolutionSource source, boolean success) {
    Counter.builder(RESOLUTIONS_TOTAL)
        .tag(SOURCE_TAG, source.metricTag())
        .tag(OUTCOME_TAG, success ? SUCCESS_OUTCOME : FAILURE_OUTCOME)
        .register(registry)
        .increment();
  }

  @Override
  public void recordEvictions(int count) {
    if (count > 0) {
      Counter.builder(EVICTIONS_TOTAL).register(registry).increment(count);
    }
  }

  @Override
  public void recordAbsoluteExpiration() {
    Counter.builder(ABSOLUTE_EXPIRATIONS_TOTAL).register(registry).increment();
  }

  @Override
  public void recordFailure(AuthSessionPolicyFailureOperation operation) {
    Counter.builder(FAILURES_TOTAL)
        .tag(OPERATION_TAG, operation.metricTag())
        .register(registry)
        .increment();
  }
}
