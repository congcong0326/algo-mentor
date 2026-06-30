package org.congcong.algomentor.ops.observability.autoconfigure;

import io.micrometer.core.instrument.MeterRegistry;
import org.congcong.algomentor.agent.core.AgentLoopObserver;
import org.congcong.algomentor.ops.observability.AgentOpsObserver;
import org.congcong.algomentor.ops.observability.AgentOpsRecorder;
import org.congcong.algomentor.ops.observability.LearningOpsRecorder;
import org.congcong.algomentor.ops.observability.MicrometerOpsRecorders;
import org.congcong.algomentor.ops.observability.NoopOpsRecorders;
import org.congcong.algomentor.ops.observability.SseOpsRecorder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class OpsObservabilityAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  SseOpsRecorder sseOpsRecorder(ObjectProvider<MeterRegistry> meterRegistry) {
    MeterRegistry registry = meterRegistry.getIfAvailable();
    return registry == null ? NoopOpsRecorders.sse() : MicrometerOpsRecorders.sse(registry);
  }

  @Bean
  @ConditionalOnMissingBean
  AgentOpsRecorder agentOpsRecorder(ObjectProvider<MeterRegistry> meterRegistry) {
    MeterRegistry registry = meterRegistry.getIfAvailable();
    return registry == null ? NoopOpsRecorders.agent() : MicrometerOpsRecorders.agent(registry);
  }

  @Bean
  @ConditionalOnMissingBean
  LearningOpsRecorder learningOpsRecorder(ObjectProvider<MeterRegistry> meterRegistry) {
    MeterRegistry registry = meterRegistry.getIfAvailable();
    return registry == null ? NoopOpsRecorders.learning() : MicrometerOpsRecorders.learning(registry);
  }

  @Bean
  @ConditionalOnMissingBean(AgentOpsObserver.class)
  AgentLoopObserver agentOpsObserver(AgentOpsRecorder agentOpsRecorder) {
    return new AgentOpsObserver(agentOpsRecorder);
  }

}
