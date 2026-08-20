package org.congcong.algomentor.api.learningplan.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.congcong.algomentor.api.config.LearningPlanGenerationRealtimeStreamProperties;
import org.congcong.algomentor.api.config.MentorConfigurationKeys;
import org.congcong.algomentor.api.config.LearningPlanRealtimeStreamLegacyConfigurationWarning;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

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

  @Bean
  @ConditionalOnMissingBean(LearningPlanRealtimeStreamLegacyConfigurationWarning.class)
  public LearningPlanRealtimeStreamLegacyConfigurationWarning learningPlanRealtimeStreamLegacyConfigurationWarning(
      Environment environment) {
    return new LearningPlanRealtimeStreamLegacyConfigurationWarning(environment);
  }

  @Bean(destroyMethod = "close")
  @ConditionalOnMissingBean(LearningPlanGenerationRealtimeEventStore.class)
  @ConditionalOnProperty(
      prefix = MentorConfigurationKeys.LEARNING_PLAN_REALTIME_STREAM_PREFIX,
      name = MentorConfigurationKeys.ENABLED,
      havingValue = MentorConfigurationKeys.TRUE,
      matchIfMissing = true)
  public LettuceLearningPlanGenerationRealtimeEventStore learningPlanGenerationRealtimeEventStore(
      LearningPlanGenerationRealtimeStreamProperties properties,
      ObjectMapper objectMapper,
      LearningPlanGenerationRealtimeEventPayloadMapper payloadMapper,
      LearningPlanDraftRevisionRealtimeEventPayloadMapper revisionPayloadMapper,
      LearningPlanGenerationRealtimeMetrics metrics
  ) {
    return new LettuceLearningPlanGenerationRealtimeEventStore(
        properties, objectMapper, payloadMapper, revisionPayloadMapper, metrics);
  }

  @Bean
  @ConditionalOnMissingBean(LearningPlanGenerationRealtimeEventStore.class)
  public LearningPlanGenerationRealtimeEventStore unavailableLearningPlanGenerationRealtimeEventStore() {
    return new UnavailableLearningPlanGenerationRealtimeEventStore();
  }

  @Bean
  @ConditionalOnMissingBean(LearningPlanDraftRevisionRealtimeEventStore.class)
  public LearningPlanDraftRevisionRealtimeEventStore unavailableLearningPlanDraftRevisionRealtimeEventStore() {
    return new UnavailableLearningPlanDraftRevisionRealtimeEventStore();
  }
}
