package org.congcong.algomentor.agent.persistence.postgres.repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.congcong.algomentor.agent.core.runtime.model.AgentMessage;
import org.congcong.algomentor.agent.core.runtime.model.AgentRunPreparationRequest;
import org.congcong.algomentor.agent.core.runtime.model.PreparedAgentRun;
import org.congcong.algomentor.agent.core.runtime.repository.AgentConversationRepository;
import org.congcong.algomentor.agent.persistence.postgres.mapper.AgentConversationMapper;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.AgentRunRecord;
import org.springframework.transaction.annotation.Transactional;

public class PostgresAgentConversationRepository implements AgentConversationRepository {

  private static final int DEFAULT_MAX_STEPS = 4;

  private final AgentConversationMapper conversationMapper;

  public PostgresAgentConversationRepository(AgentConversationMapper conversationMapper) {
    this.conversationMapper = conversationMapper;
  }

  @Override
  @Transactional
  public PreparedAgentRun createOrReuseRun(AgentRunPreparationRequest request) {
    conversationMapper.lockIdempotencyKey(request.idempotencyKey());

    Long existingRunId = conversationMapper.findRunIdByIdempotencyKey(request.idempotencyKey());
    if (existingRunId != null) {
      return existingDraft(existingRunId);
    }

    long taskId = request.taskId() == null ? createTask(request) : request.taskId();
    long turnId = conversationMapper.insertTurn(taskId);
    long userMessageId = conversationMapper.insertUserMessage(
        taskId,
        turnId,
        request.userMessage(),
        estimateTokens(request.userMessage()));
    String runUuid = UUID.randomUUID().toString();
    long runId = conversationMapper.insertRun(
        taskId,
        turnId,
        runUuid,
        request.idempotencyKey(),
        DEFAULT_MAX_STEPS);
    conversationMapper.attachTurnUserMessageAndRun(turnId, userMessageId, runId);

    return new PreparedAgentRun(
        taskId,
        turnId,
        runId,
        runUuid,
        request.idempotencyKey(),
        request.systemPrompt(),
        null,
        request.metadata());
  }

  @Override
  public List<AgentMessage> recentMessages(long taskId, int messageLimit) {
    return conversationMapper.recentMessages(taskId, messageLimit).stream()
        .sorted(Comparator.comparingLong(AgentMessage::sequenceNo))
        .toList();
  }

  private PreparedAgentRun existingDraft(long runId) {
    AgentRunRecord record = conversationMapper.findRunRecord(runId);
    return new PreparedAgentRun(
        record.taskId(),
        record.turnId(),
        record.runId(),
        record.runUuid(),
        record.idempotencyKey(),
        record.systemPrompt(),
        null,
        Map.of("idempotentReplay", true));
  }

  private long createTask(AgentRunPreparationRequest request) {
    return conversationMapper.insertTask(
        request.userId(),
        title(request.userMessage()),
        request.systemPrompt());
  }

  private String title(String userMessage) {
    return userMessage.length() > 80 ? userMessage.substring(0, 80) : userMessage;
  }

  private int estimateTokens(String content) {
    return Math.max(1, content.length() / 4);
  }
}
