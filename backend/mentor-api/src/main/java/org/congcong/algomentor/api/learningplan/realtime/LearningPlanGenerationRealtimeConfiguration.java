package org.congcong.algomentor.api.learningplan.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.congcong.algomentor.api.config.LearningPlanGenerationRealtimeStreamProperties;
import org.congcong.algomentor.api.config.MentorConfigurationKeys;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 学习计划首次草案独立 Redis Stream 连接的装配边界。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LearningPlanGenerationRealtimeStreamProperties.class)
public class LearningPlanGenerationRealtimeConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public LearningPlanGenerationRealtimeMetrics learningPlanGenerationRealtimeMetrics(
      ObjectProvider<MeterRegistry> registryProvider
  ) {
    return new LearningPlanGenerationRealtimeMetrics(registryProvider.getIfAvailable());
  }

  @Bean(destroyMethod = "close")
  @ConditionalOnMissingBean(LearningPlanGenerationRealtimeEventStore.class)
  @ConditionalOnProperty(
      prefix = MentorConfigurationKeys.LEARNING_PLAN_GENERATION_REALTIME_STREAM_PREFIX,
      name = MentorConfigurationKeys.ENABLED,
      havingValue = MentorConfigurationKeys.TRUE,
      matchIfMissing = true)
  public LearningPlanGenerationRealtimeEventStore learningPlanGenerationRealtimeEventStore(
      LearningPlanGenerationRealtimeStreamProperties properties,
      ObjectMapper objectMapper,
      LearningPlanGenerationRealtimeEventPayloadMapper payloadMapper,
      LearningPlanGenerationRealtimeMetrics metrics
  ) {
    return new LettuceLearningPlanGenerationRealtimeEventStore(properties, objectMapper, payloadMapper, metrics);
  }

  @Bean
  @ConditionalOnMissingBean(LearningPlanGenerationRealtimeEventStore.class)
  public LearningPlanGenerationRealtimeEventStore unavailableLearningPlanGenerationRealtimeEventStore() {
    return new UnavailableLearningPlanGenerationRealtimeEventStore();
  }
}
