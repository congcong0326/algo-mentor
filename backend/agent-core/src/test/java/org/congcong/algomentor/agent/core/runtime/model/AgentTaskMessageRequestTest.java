package org.congcong.algomentor.agent.core.runtime.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentTaskMessageRequestTest {

  @Test
  void rejectsNonPositiveTaskRefId() {
    assertThatThrownBy(() -> new AgentTaskRef(0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("task id");
  }

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
  void appliesTaskCreationRequestDefaults() {
    AgentTaskCreationRequest request = new AgentTaskCreationRequest(null, " ", null, null);

    assertThat(request.title()).isEqualTo("practice-session");
    assertThat(request.systemPrompt()).isEmpty();
    assertThat(request.metadata()).isEmpty();
  }

  @Test
  void rejectsNonPositiveTaskCreationUserId() {
    assertThatThrownBy(() -> new AgentTaskCreationRequest(0L, "title", "system", Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("user id");

    assertThatThrownBy(() -> new AgentTaskCreationRequest(-1L, "title", "system", Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("user id");
  }

  @Test
  void copiesTaskCreationMetadataAndReturnsUnmodifiableMetadata() {
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("scenario", "PRACTICE_CHAT");

    AgentTaskCreationRequest request = new AgentTaskCreationRequest(42L, "title", "system", metadata);
    metadata.put("scenario", "OTHER");

    assertThat(request.metadata()).containsEntry("scenario", "PRACTICE_CHAT");
    assertThatThrownBy(() -> request.metadata().put("x", "y"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void rejectsNonPositiveSeedMessageTaskId() {
    assertThatThrownBy(() -> new AgentAssistantSeedMessageRequest(0, "x", Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("task id");
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
  void copiesSeedMessageMetadataAndReturnsUnmodifiableMetadata() {
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("messageType", "PROBLEM_STATEMENT");

    AgentAssistantSeedMessageRequest request = new AgentAssistantSeedMessageRequest(10L, "seed", metadata);
    metadata.put("messageType", "CHAT");

    assertThat(request.metadata()).containsEntry("messageType", "PROBLEM_STATEMENT");
    assertThatThrownBy(() -> request.metadata().put("x", "y"))
        .isInstanceOf(UnsupportedOperationException.class);
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

  @Test
  void compatibilityConstructorSetsEmptyUserMessageMetadata() {
    AgentRunPreparationRequest request = new AgentRunPreparationRequest(
        10L,
        42L,
        "hello",
        "idem-1",
        "system",
        Map.of("run", true));

    assertThat(request.userMessageMetadata()).isEmpty();
  }
}
