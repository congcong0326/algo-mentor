package org.congcong.algomentor.api.practice.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.congcong.algomentor.api.config.MentorConfigurationKeys;
import org.congcong.algomentor.api.config.PracticeRealtimeStreamProperties;
import org.congcong.algomentor.api.service.LlmStreamSseMapper;
import org.congcong.algomentor.ops.observability.PracticeRealtimeOpsRecorder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Practice Chat Redis Stream 的独立连接装配边界。 */
@Configuration(proxyBeanMethods = false)
public class PracticeRealtimeConfiguration {

  @Bean(destroyMethod = "close")
  @ConditionalOnBean(LlmStreamSseMapper.class)
  @ConditionalOnMissingBean(PracticeRealtimeEventStore.class)
  @ConditionalOnProperty(
      prefix = MentorConfigurationKeys.PRACTICE_REALTIME_STREAM_PREFIX,
      name = MentorConfigurationKeys.ENABLED,
      havingValue = MentorConfigurationKeys.TRUE,
      matchIfMissing = true)
  public PracticeRealtimeEventStore practiceRealtimeEventStore(
      PracticeRealtimeStreamProperties properties,
      ObjectMapper objectMapper,
      LlmStreamSseMapper sseMapper,
      PracticeRealtimeOpsRecorder opsRecorder
  ) {
    return new LettucePracticeRealtimeEventStore(properties, objectMapper, sseMapper, opsRecorder);
  }

  @Bean
  @ConditionalOnMissingBean(PracticeRealtimeEventStore.class)
  public PracticeRealtimeEventStore unavailablePracticeRealtimeEventStore() {
    return new UnavailablePracticeRealtimeEventStore();
  }
}
