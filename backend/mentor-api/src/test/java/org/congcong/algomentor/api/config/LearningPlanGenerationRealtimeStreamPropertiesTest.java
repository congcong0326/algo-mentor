package org.congcong.algomentor.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

class LearningPlanGenerationRealtimeStreamPropertiesTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withUserConfiguration(PropertiesConfiguration.class);

  @Test
  void bindsTheCanonicalRealtimeStreamPrefix() {
    contextRunner.withPropertyValues(
        "algo-mentor.learning-plan.realtime-stream.host=redis.internal",
        "algo-mentor.learning-plan.realtime-stream.read-block=1s")
        .run(context -> {
          LearningPlanGenerationRealtimeStreamProperties properties = context.getBean(
              LearningPlanGenerationRealtimeStreamProperties.class);

          assertThat(properties.getHost()).isEqualTo("redis.internal");
          assertThat(properties.getReadBlock()).isEqualTo(java.time.Duration.ofSeconds(1));
        });
  }

  @Test
  void applicationResourcesKeepTheLegacyPrefixAsTheFallbackBehindCanonicalEnvironmentValues()
      throws IOException {
    String applicationYaml = new ClassPathResource("application.yml")
        .getContentAsString(StandardCharsets.UTF_8);
    String preprodYaml = new ClassPathResource("application-preprod.yml")
        .getContentAsString(StandardCharsets.UTF_8);

    assertThat(applicationYaml).contains(
        "host: ${LEARNING_PLAN_REALTIME_STREAM_HOST:${algo-mentor.learning-plan.generation.realtime-stream.host:");
    assertThat(preprodYaml).contains(
        "host: ${LEARNING_PLAN_REALTIME_STREAM_HOST:${algo-mentor.learning-plan.generation.realtime-stream.host:");
  }

  @Test
  void recognizesDottedAndEnvironmentStyleLegacyConfiguration() {
    MockEnvironment dotted = new MockEnvironment().withProperty(
        MentorConfigurationKeys.LEARNING_PLAN_GENERATION_REALTIME_STREAM_PREFIX + ".host", "legacy-host");
    MockEnvironment environmentStyle = new MockEnvironment().withProperty(
        "LEARNING_PLAN_GENERATION_REALTIME_STREAM_READ_BLOCK", "2s");

    assertThat(new LearningPlanRealtimeStreamLegacyConfigurationWarning(dotted).hasLegacyConfiguration()).isTrue();
    assertThat(new LearningPlanRealtimeStreamLegacyConfigurationWarning(environmentStyle).hasLegacyConfiguration())
        .isTrue();
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(LearningPlanGenerationRealtimeStreamProperties.class)
  static class PropertiesConfiguration {
  }
}
