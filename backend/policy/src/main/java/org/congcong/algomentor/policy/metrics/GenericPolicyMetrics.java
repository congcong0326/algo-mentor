package org.congcong.algomentor.policy.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.Objects;

/** 通用策略指标的低基数记录边界。 */
public final class GenericPolicyMetrics {

  private final MeterRegistry meterRegistry;

  public GenericPolicyMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  public void recordResolve(String typeCode, String result) {
    increment("generic.policy.resolve", typeCode, result);
  }

  public void recordCompile(String typeCode, String result) {
    increment("generic.policy.compile", typeCode, result);
  }

  public void recordCacheInvalidation(String typeCode, String result) {
    increment("generic.policy.cache.invalidate", typeCode, result);
  }

  public void recordAdminWrite(String operation, String result) {
    if (meterRegistry != null) {
      meterRegistry.counter("generic.policy.admin.write", "operation", operation, "result", result).increment();
    }
  }

  private void increment(String metricName, String typeCode, String result) {
    if (meterRegistry != null) {
      meterRegistry.counter(
          metricName,
          "typeCode", Objects.requireNonNull(typeCode, "typeCode must not be null"),
          "result", result).increment();
    }
  }
}
