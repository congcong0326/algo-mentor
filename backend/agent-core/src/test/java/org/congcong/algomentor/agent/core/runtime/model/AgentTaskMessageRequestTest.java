package org.congcong.algomentor.agent.core.runtime.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentTaskMessageRequestTest {

  @Test
  void normalizesTaskCreationRequestMetadata() {
    AgentTaskCreationRequest request = new AgentTaskCreationRequest(
        42L,
        "Two Sum",
        "system",
        Map.of("scenario", "PRACTICE_CHAT"));

    assertThat(request.userId()).isEqualTo(42L);
    assertThat(request.title()).isEqualTo("Two Sum");
    assertThat(request.systemPrompt()).isEqualTo("system");
    assertThat(request.metadata()).containsEntry("scenario", "PRACTICE_CHAT");
  }

  @Test
  void rejectsBlankSeedContent() {
    assertThatThrownBy(() -> new AgentAssistantSeedMessageRequest(
        10L,
        "",
        Map.of("messageType", "PROBLEM_STATEMENT")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("seed message content");
  }

  @Test
  void storesMessageMetadataOnRunPreparationRequest() {
    AgentRunPreparationRequest request = new AgentRunPreparationRequest(
        10L,
        42L,
        "hello",
        "idem-1",
        "system",
        Map.of("run", true),
        Map.of("messageType", "CHAT"));

    assertThat(request.metadata()).containsEntry("run", true);
    assertThat(request.userMessageMetadata()).containsEntry("messageType", "CHAT");
  }
}
