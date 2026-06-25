package org.congcong.algomentor.agent.persistence.postgres.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.congcong.algomentor.agent.core.runtime.repository.AgentConversationRepository;
import org.congcong.algomentor.agent.core.runtime.repository.AgentTaskMessageRepository;
import org.congcong.algomentor.agent.persistence.postgres.mapper.AgentConversationMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class AgentPostgresPersistenceConfigurationTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withUserConfiguration(TestConfig.class, AgentPostgresPersistenceConfiguration.class);

  @Test
  void repositoryBeanIsVisibleAsConversationAndTaskMessageRepository() {
    contextRunner.run(context -> {
      assertThat(context).hasSingleBean(AgentConversationRepository.class);
      assertThat(context).hasSingleBean(AgentTaskMessageRepository.class);
      assertThat(context.getBean(AgentConversationRepository.class))
          .isSameAs(context.getBean(AgentTaskMessageRepository.class));
    });
  }

  @Configuration(proxyBeanMethods = false)
  static class TestConfig {

    @Bean
    AgentConversationMapper agentConversationMapper() {
      return mock(AgentConversationMapper.class);
    }
  }
}
