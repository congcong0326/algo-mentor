package org.congcong.algomentor.ai.governance.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import org.congcong.algomentor.agent.core.AgentRunResult;
import org.congcong.algomentor.ai.governance.admission.AiRunAdmission;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.llm.core.response.LlmUsage;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.junit.jupiter.api.Test;

class AiRunMetricsObserverTest {

  @Test
  void recordsRunCountersDurationTokensAndRejections() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    AiRunMetricsObserver observer = new AiRunMetricsObserver(registry);
    AiRunAdmission admission = AiRunGovernanceObserverTest.admittedRun();
    var context = AiRunGovernanceObserverTest.contextWithAdmission(admission);

    observer.onRunStart(context);
    observer.onLlmEvent(context, 1, new LlmStreamEvent.Usage(new LlmUsage(5, 8, 0, 1, 14)));
    observer.onRunEnd(context, new AgentRunResult(1, LlmFinishReason.STOP, Map.of()));

    assertThat(registry.find("ai.run.requests").tag("status", "COMPLETED").counter().count()).isEqualTo(1);
    assertThat(registry.find("ai.run.tokens").tag("type", "total").counter().count()).isEqualTo(14);
  }
}
