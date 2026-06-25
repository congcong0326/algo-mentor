package org.congcong.algomentor.agent.persistence.postgres.repository;

import java.util.Optional;
import org.congcong.algomentor.agent.core.runtime.model.AgentMessage;
import org.congcong.algomentor.agent.core.runtime.model.AgentTurnMessages;
import org.congcong.algomentor.agent.core.runtime.repository.AgentTurnMessageLookupRepository;
import org.congcong.algomentor.agent.persistence.postgres.mapper.AgentConversationMapper;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.AgentTurnMessagesRow;

public class PostgresAgentTurnMessageLookupRepository implements AgentTurnMessageLookupRepository {

  private final AgentConversationMapper mapper;

  public PostgresAgentTurnMessageLookupRepository(AgentConversationMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Optional<AgentTurnMessages> findByRunId(long runId) {
    if (runId < 1) {
      throw new IllegalArgumentException("Agent run id must be positive");
    }
    return Optional.ofNullable(mapper.findTurnMessagesByRunId(runId)).map(this::toDomain);
  }

  private AgentTurnMessages toDomain(AgentTurnMessagesRow row) {
    AgentMessage userMessage = new AgentMessage(
        row.userMessageId(),
        row.taskId(),
        row.userSequenceNo(),
        AgentMessage.Role.USER,
        row.userContent(),
        row.userCreatedAt(),
        row.userMetadata());
    AgentMessage assistantMessage = row.assistantMessageId() == null
        ? null
        : new AgentMessage(
            row.assistantMessageId(),
            row.taskId(),
            row.assistantSequenceNo(),
            AgentMessage.Role.ASSISTANT,
            row.assistantContent(),
            row.assistantCreatedAt(),
            row.assistantMetadata());
    return new AgentTurnMessages(row.runId(), row.turnId(), userMessage, assistantMessage);
  }
}
