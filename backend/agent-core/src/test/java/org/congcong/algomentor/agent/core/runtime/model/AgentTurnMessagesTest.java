package org.congcong.algomentor.agent.core.runtime.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AgentTurnMessagesTest {

  @Test
  void acceptsRequiredUserMessageAndNullableAssistantMessage() {
    AgentMessage user = message(10, AgentMessage.Role.USER);
    AgentMessage assistant = message(11, AgentMessage.Role.ASSISTANT);

    AgentTurnMessages messages = new AgentTurnMessages(80L, 70L, user, assistant);

    assertThat(messages.runId()).isEqualTo(80L);
    assertThat(messages.turnId()).isEqualTo(70L);
    assertThat(messages.userMessage()).isEqualTo(user);
    assertThat(messages.assistantMessage()).contains(assistant);
  }

  @Test
  void rejectsAssistantMessageWithUserRole() {
    AgentMessage user = message(10, AgentMessage.Role.USER);

    assertThatThrownBy(() -> new AgentTurnMessages(80L, 70L, user, user))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("assistant message");
  }

  @Test
  void rejectsNonPositiveIds() {
    AgentMessage user = message(10, AgentMessage.Role.USER);

    assertThatThrownBy(() -> new AgentTurnMessages(0L, 70L, user, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ids must be positive");
    assertThatThrownBy(() -> new AgentTurnMessages(80L, 0L, user, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ids must be positive");
  }

  @Test
  void rejectsMissingUserMessage() {
    assertThatThrownBy(() -> new AgentTurnMessages(80L, 70L, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("user message");
  }

  @Test
  void rejectsUserMessageWithAssistantRole() {
    AgentMessage assistant = message(11, AgentMessage.Role.ASSISTANT);

    assertThatThrownBy(() -> new AgentTurnMessages(80L, 70L, assistant, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("user message");
  }

  @Test
  void exposesEmptyAssistantWhenMissing() {
    AgentTurnMessages messages = new AgentTurnMessages(80L, 70L, message(10, AgentMessage.Role.USER), null);

    assertThat(messages.assistantMessage()).isEqualTo(Optional.empty());
  }

  private AgentMessage message(long id, AgentMessage.Role role) {
    return new AgentMessage(id, 5L, id, role, "content " + id, Instant.parse("2026-01-01T00:00:00Z"));
  }
}
