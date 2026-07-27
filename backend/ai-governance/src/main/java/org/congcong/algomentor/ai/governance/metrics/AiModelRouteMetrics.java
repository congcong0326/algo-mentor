package org.congcong.algomentor.ai.governance.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.congcong.algomentor.ai.governance.model.AiBusinessScenario;

/** 模型路由解析的低基数运行指标。 */
public final class AiModelRouteMetrics {

  public static final String RESOLUTIONS_TOTAL = "ai_model_route_resolutions_total";
  public static final String RESOLUTION_SECONDS = "ai_model_route_resolution_seconds";

  private final MeterRegistry meterRegistry;

  public AiModelRouteMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  public void record(AiBusinessScenario scenario, String result, long startedAtNanos) {
    if (meterRegistry == null) {
      return;
    }
    String scenarioCode = scenario.code();
    Counter.builder(RESOLUTIONS_TOTAL)
        .tag("scenario", scenarioCode)
        .tag("result", result)
        .register(meterRegistry)
        .increment();
    Timer.builder(RESOLUTION_SECONDS)
        .tag("scenario", scenarioCode)
        .register(meterRegistry)
        .record(Duration.ofNanos(Math.max(0L, System.nanoTime() - startedAtNanos)));
  }
}
