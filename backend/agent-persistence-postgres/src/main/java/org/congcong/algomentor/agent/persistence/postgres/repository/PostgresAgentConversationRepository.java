package org.congcong.algomentor.agent.persistence.postgres.repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationMode;
import org.congcong.algomentor.agent.core.runtime.model.AgentActiveRun;
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
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.AgentRunInsert;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.AgentRunRecord;
import org.springframework.transaction.annotation.Transactional;

public class PostgresAgentConversationRepository implements AgentConversationRepository, AgentTaskMessageRepository {

  private static final String AUDIT_TASK_TITLE = "agent-run";

  private final AgentConversationMapper conversationMapper;

  public PostgresAgentConversationRepository(AgentConversationMapper conversationMapper) {
    this.conversationMapper = conversationMapper;
  }

  @Override
  @Transactional
  public PreparedAgentRun createOrReuseRun(AgentRunPreparationRequest request) {
    Objects.requireNonNull(request, "agent run preparation request must not be null");
    AgentRunRecord retrySource = retrySource(request);
    conversationMapper.lockIdempotencyKey(request.idempotencyKey());

    Long existingRunId = conversationMapper.findRunIdByIdempotencyKey(request.idempotencyKey());
    if (existingRunId != null) {
      return existingDraft(existingRunId);
    }
    if (retrySource != null) {
      return createRetryRun(request, retrySource);
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
    long runId = conversationMapper.insertRun(runInsert(request, taskId, turnId, runUuid, null, null));
    conversationMapper.attachTurnUserMessageAndRun(turnId, userMessageId, runId);

    return preparedRun(request, taskId, turnId, runId, runUuid, request.systemPrompt(), null, null);
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
  public List<AgentMessage> recentMessagesBeforeTurn(long taskId, long turnId, int messageLimit) {
    return conversationMapper.recentMessagesBeforeTurn(taskId, turnId, messageLimit).stream()
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

  @Override
  public Optional<AgentActiveRun> activeRun(long taskId) {
    return Optional.ofNullable(conversationMapper.findActiveRun(taskId));
  }

  private PreparedAgentRun existingDraft(long runId) {
    AgentRunRecord record = conversationMapper.findRunRecord(runId);
    if (record == null) {
      throw new IllegalStateException("Agent run was not found: " + runId);
    }
    return new PreparedAgentRun(
        record.taskId(),
        record.turnId(),
        record.runId(),
        record.runUuid(),
        record.idempotencyKey(),
        record.systemPrompt(),
        null,
        Map.of(AgentRuntimeMetadataKeys.IDEMPOTENT_REPLAY, true),
        record.agentKey(),
        invocationMode(record.triggerType()),
        record.parentRunId(),
        record.parentStepIndex(),
        record.retryOfRunId(),
        record.maxSteps());
  }

  private long createTask(AgentRunPreparationRequest request) {
    return conversationMapper.insertTask(
        request.userId(),
        AUDIT_TASK_TITLE,
        request.systemPrompt(),
        Map.of());
  }

  private AgentRunRecord retrySource(AgentRunPreparationRequest request) {
    if (request.retryOfRunId() == null) {
      return null;
    }
    AgentRunRecord source = conversationMapper.findRunRecord(request.retryOfRunId());
    if (source == null) {
      throw new IllegalArgumentException("Agent retry source run was not found: " + request.retryOfRunId());
    }
    if (request.idempotencyKey().equals(source.idempotencyKey())) {
      throw new IllegalArgumentException("Agent retry requires a new idempotency key");
    }
    if (request.taskId() != null && request.taskId() != source.taskId()) {
      throw new IllegalArgumentException("Agent retry task must match its source run");
    }
    return source;
  }

  private PreparedAgentRun createRetryRun(
      AgentRunPreparationRequest request,
      AgentRunRecord source
  ) {
    Long parentRunId = request.parentRunId() == null ? source.parentRunId() : request.parentRunId();
    Integer parentStepIndex = request.parentStepIndex() == null
        ? source.parentStepIndex()
        : request.parentStepIndex();
    String agentKey = request.agentKey() == null ? source.agentKey() : request.agentKey();
    AgentInvocationMode mode = invocationMode(source.triggerType());
    String runUuid = UUID.randomUUID().toString();
    long runId = conversationMapper.insertRun(new AgentRunInsert(
        source.taskId(),
        source.turnId(),
        runUuid,
        request.idempotencyKey(),
        request.maxSteps(),
        agentKey,
        mode.databaseValue(),
        parentRunId,
        parentStepIndex,
        source.runId()));
    conversationMapper.attachTurnRun(source.turnId(), runId);
    return new PreparedAgentRun(
        source.taskId(),
        source.turnId(),
        runId,
        runUuid,
        request.idempotencyKey(),
        source.systemPrompt(),
        null,
        request.metadata(),
        agentKey,
        mode,
        parentRunId,
        parentStepIndex,
        source.runId(),
        request.maxSteps());
  }

  private AgentRunInsert runInsert(
      AgentRunPreparationRequest request,
      long taskId,
      long turnId,
      String runUuid,
      Long retryOfRunId,
      ParentLink parentLink
  ) {
    return new AgentRunInsert(
        taskId,
        turnId,
        runUuid,
        request.idempotencyKey(),
        request.maxSteps(),
        request.agentKey(),
        request.mode().databaseValue(),
        parentLink == null ? request.parentRunId() : parentLink.runId(),
        parentLink == null ? request.parentStepIndex() : parentLink.stepIndex(),
        retryOfRunId);
  }

  private PreparedAgentRun preparedRun(
      AgentRunPreparationRequest request,
      long taskId,
      long turnId,
      long runId,
      String runUuid,
      String systemPrompt,
      Long retryOfRunId,
      ParentLink parentLink
  ) {
    return new PreparedAgentRun(
        taskId,
        turnId,
        runId,
        runUuid,
        request.idempotencyKey(),
        systemPrompt,
        null,
        request.metadata(),
        request.agentKey(),
        request.mode(),
        parentLink == null ? request.parentRunId() : parentLink.runId(),
        parentLink == null ? request.parentStepIndex() : parentLink.stepIndex(),
        retryOfRunId,
        request.maxSteps());
  }

  private AgentInvocationMode invocationMode(String triggerType) {
    if (triggerType == null || triggerType.isBlank()) {
      return AgentInvocationMode.USER_ENTRY;
    }
    return AgentInvocationMode.valueOf(triggerType);
  }

  private record ParentLink(Long runId, Integer stepIndex) {
    private ParentLink {
      if ((runId == null) != (stepIndex == null)) {
        throw new IllegalArgumentException("Agent parent run and step index must be provided together");
      }
    }
  }

  private int estimateTokens(String content) {
    return Math.max(1, content.length() / 4);
  }
}
