package org.congcong.algomentor.agent.persistence.postgres.repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.congcong.algomentor.agent.core.runtime.model.AgentAssistantSeedMessageRequest;
import org.congcong.algomentor.agent.core.runtime.model.AgentMessage;
import org.congcong.algomentor.agent.core.runtime.model.AgentRunPreparationRequest;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.agent.core.runtime.model.AgentTaskCreationRequest;
import org.congcong.algomentor.agent.core.runtime.model.AgentTaskRef;
import org.congcong.algomentor.agent.core.runtime.model.PreparedAgentRun;
import org.congcong.algomentor.agent.core.runtime.repository.AgentConversationRepository;
import org.congcong.algomentor.agent.core.runtime.repository.AgentTaskMessageRepository;
import org.congcong.algomentor.agent.persistence.postgres.mapper.AgentConversationMapper;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.AgentRunRecord;
import org.springframework.transaction.annotation.Transactional;

public class PostgresAgentConversationRepository implements AgentConversationRepository, AgentTaskMessageRepository {

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
        estimateTokens(request.userMessage()),
        request.userMessageMetadata());
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
  @Transactional(readOnly = true)
  public Optional<PreparedAgentRun> findRunByIdempotencyKey(String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new IllegalArgumentException("Agent run idempotency key must not be blank");
    }
    Long runId = conversationMapper.findRunIdByIdempotencyKey(idempotencyKey);
    return runId == null ? Optional.empty() : Optional.of(existingDraft(runId));
  }

  @Override
  public List<AgentMessage> recentMessages(long taskId, int messageLimit) {
    return conversationMapper.recentMessages(taskId, messageLimit).stream()
        .sorted(Comparator.comparingLong(AgentMessage::sequenceNo))
        .toList();
  }

  @Override
  @Transactional
  public AgentTaskRef createTask(AgentTaskCreationRequest request) {
    long taskId = conversationMapper.insertTask(
        request.userId(),
        request.title(),
        request.systemPrompt(),
        request.metadata());
    return new AgentTaskRef(taskId);
  }

  @Override
  @Transactional
  public AgentMessage createAssistantSeedMessage(AgentAssistantSeedMessageRequest request) {
    long turnId = conversationMapper.insertTurn(request.taskId());
    long messageId = conversationMapper.insertAssistantSeedMessage(
        request.taskId(),
        turnId,
        request.content(),
        estimateTokens(request.content()),
        request.metadata());
    conversationMapper.attachTurnAssistantSeedMessage(turnId, messageId);
    AgentMessage message = conversationMapper.findMessageById(messageId);
    if (message == null) {
      throw new IllegalStateException("Inserted assistant seed message was not found: " + messageId);
    }
    return message;
  }

  @Override
  public List<AgentMessage> messages(long taskId, int messageLimit) {
    return conversationMapper.messages(taskId, messageLimit).stream()
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
        Map.of(AgentRuntimeMetadataKeys.IDEMPOTENT_REPLAY, true));
  }

  private long createTask(AgentRunPreparationRequest request) {
    return conversationMapper.insertTask(
        request.userId(),
        title(request.userMessage()),
        request.systemPrompt(),
        request.metadata());
  }

  private String title(String userMessage) {
    return userMessage.length() > 80 ? userMessage.substring(0, 80) : userMessage;
  }

  private int estimateTokens(String content) {
    return Math.max(1, content.length() / 4);
  }
}
