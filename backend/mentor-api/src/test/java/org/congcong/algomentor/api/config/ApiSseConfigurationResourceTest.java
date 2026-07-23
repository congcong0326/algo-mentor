package org.congcong.algomentor.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class ApiSseConfigurationResourceTest {

  @Test
  void applicationYamlExposesPracticeMessageTimeoutEnvironmentOverride() throws IOException {
    String applicationYaml = new ClassPathResource("application.yml")
        .getContentAsString(StandardCharsets.UTF_8);

    assertThat(applicationYaml)
        .contains("practice-message-timeout: ${PRACTICE_MESSAGE_SSE_TIMEOUT:6m}")
        .contains("agent-conversation-timeout: ${AGENT_CONVERSATION_SSE_TIMEOUT:6m}");
  }

  @Test
  void apiSsePropertiesUseSixMinuteAgentConversationTimeoutByDefault() {
    ApiSseProperties properties = new ApiSseProperties();

    assertThat(properties.getAgentConversationTimeout()).isEqualTo(Duration.ofMinutes(6));
    assertThat(properties.agentConversationTimeoutMillis()).isEqualTo(360_000L);
  }
}
