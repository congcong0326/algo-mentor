package org.congcong.algomentor.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class AgentExecutorPropertiesTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withUserConfiguration(PropertiesConfiguration.class);

  @Test
  void bindsExecutorSettings() {
    contextRunner.withPropertyValues(
            "algo-mentor.agent.executor.groups.practice=8",
            "algo-mentor.agent.executor.groups.learning-plan=4",
            "algo-mentor.agent.executor.groups.learner-profile-background=2",
            "algo-mentor.agent.executor.keep-alive=45s",
            "algo-mentor.agent.executor.shutdown-timeout=20s",
            "algo-mentor.agent.executor.thread-name-prefix=custom-agent-")
        .run(context -> {
          assertThat(context).hasNotFailed();
          AgentExecutorProperties properties = context.getBean(AgentExecutorProperties.class);
          assertThat(properties.getGroups()).containsExactlyInAnyOrderEntriesOf(java.util.Map.of(
              "practice", 8,
              "learning-plan", 4,
              "learner-profile-background", 2));
          assertThat(properties.getKeepAlive()).isEqualTo(Duration.ofSeconds(45));
          assertThat(properties.getShutdownTimeout()).isEqualTo(Duration.ofSeconds(20));
          assertThat(properties.getThreadNamePrefix()).isEqualTo("custom-agent-");
        });
  }

  @Test
  void doesNotBindLegacyPoolSizeProperties() {
    contextRunner.withPropertyValues(
            "algo-mentor.agent.executor.core-pool-size=101",
            "algo-mentor.agent.executor.max-pool-size=100")
        .run(context -> {
          assertThat(context).hasNotFailed();
          assertThat(context.getBean(AgentExecutorProperties.class).getGroups()).containsEntry("practice", 27);
        });
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(AgentExecutorProperties.class)
  static class PropertiesConfiguration {
  }
}
