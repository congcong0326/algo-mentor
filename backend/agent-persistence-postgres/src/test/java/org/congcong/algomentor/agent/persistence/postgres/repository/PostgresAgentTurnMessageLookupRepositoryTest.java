package org.congcong.algomentor.agent.persistence.postgres.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.congcong.algomentor.agent.core.runtime.model.AgentTurnMessages;
import org.congcong.algomentor.agent.persistence.postgres.mapper.AgentConversationMapper;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.AgentTurnMessagesRow;
import org.junit.jupiter.api.Test;

class PostgresAgentTurnMessageLookupRepositoryTest {

  @Test
  void mapsTurnMessagesWithAssistant() {
    AgentConversationMapper mapper = mock(AgentConversationMapper.class);
    when(mapper.findTurnMessagesByRunId(80L)).thenReturn(row(11L));
    PostgresAgentTurnMessageLookupRepository repository = new PostgresAgentTurnMessageLookupRepository(mapper);

    Optional<AgentTurnMessages> result = repository.findByRunId(80L);

    assertThat(result).isPresent();
    assertThat(result.get().runId()).isEqualTo(80L);
    assertThat(result.get().turnId()).isEqualTo(70L);
    assertThat(result.get().userMessage().id()).isEqualTo(10L);
    assertThat(result.get().assistantMessage()).hasValueSatisfying(message ->
        assertThat(message.id()).isEqualTo(11L));
  }

  @Test
  void mapsMissingAssistantAsEmpty() {
    AgentConversationMapper mapper = mock(AgentConversationMapper.class);
    when(mapper.findTurnMessagesByRunId(80L)).thenReturn(row(null));
    PostgresAgentTurnMessageLookupRepository repository = new PostgresAgentTurnMessageLookupRepository(mapper);

    Optional<AgentTurnMessages> result = repository.findByRunId(80L);

    assertThat(result).isPresent();
    assertThat(result.get().assistantMessage()).isEmpty();
  }

  @Test
  void returnsEmptyWhenRunMissing() {
    AgentConversationMapper mapper = mock(AgentConversationMapper.class);
    when(mapper.findTurnMessagesByRunId(80L)).thenReturn(null);
    PostgresAgentTurnMessageLookupRepository repository = new PostgresAgentTurnMessageLookupRepository(mapper);

    assertThat(repository.findByRunId(80L)).isEmpty();
  }

  @Test
  void rejectsInvalidRunId() {
    PostgresAgentTurnMessageLookupRepository repository =
        new PostgresAgentTurnMessageLookupRepository(mock(AgentConversationMapper.class));

    assertThatThrownBy(() -> repository.findByRunId(0L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Agent run id must be positive");
  }

  private AgentTurnMessagesRow row(Long assistantMessageId) {
    return new AgentTurnMessagesRow(
        80L,
        70L,
        5L,
        10L,
        1L,
        "user content",
        Instant.parse("2026-01-01T00:00:00Z"),
        Map.of("messageType", "CHAT"),
        assistantMessageId,
        assistantMessageId == null ? null : 2L,
        assistantMessageId == null ? null : "assistant content",
        assistantMessageId == null ? null : Instant.parse("2026-01-01T00:01:00Z"),
        assistantMessageId == null ? null : Map.of("messageType", "CHAT"));
  }
}
