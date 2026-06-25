package org.congcong.algomentor.agent.persistence.postgres.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.congcong.algomentor.agent.core.runtime.model.AgentAssistantSeedMessageRequest;
import org.congcong.algomentor.agent.core.runtime.model.AgentActiveRun;
import org.congcong.algomentor.agent.core.runtime.model.AgentMessage;
import org.congcong.algomentor.agent.core.runtime.model.AgentRunPreparationRequest;
import org.congcong.algomentor.agent.core.runtime.model.PreparedAgentRun;
import org.congcong.algomentor.agent.persistence.postgres.mapper.AgentConversationMapper;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.AgentRunRecord;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.AgentTurnMessagesRow;
import org.junit.jupiter.api.Test;

class PostgresAgentConversationRepositoryTest {

  private final FakeConversationMapper mapper = new FakeConversationMapper();
  private final PostgresAgentConversationRepository repository = new PostgresAgentConversationRepository(mapper);

  @Test
  void createsTaskTurnUserMessageAndRunForNewRequest() {
    mapper.nextTaskId = 101L;
    mapper.nextTurnId = 201L;
    mapper.nextMessageId = 301L;
    mapper.nextRunId = 401L;

    PreparedAgentRun run = repository.createOrReuseRun(new AgentRunPreparationRequest(
        null,
        7L,
        "learn monotonic stack",
        "idem-1",
        "system prompt",
        Map.of("triggerType", "user_request")));

    assertThat(run.taskId()).isEqualTo(101);
    assertThat(run.turnId()).isEqualTo(201);
    assertThat(run.runId()).isEqualTo(401);
    assertThat(run.runUuid()).isNotBlank();
    assertThat(run.requestId()).isEqualTo("idem-1");
    assertThat(run.systemPrompt()).isEqualTo("system prompt");
    assertThat(run.metadata()).containsEntry("triggerType", "user_request");
    assertThat(mapper.calls).containsExactly(
        "lockIdempotencyKey:idem-1",
        "findRunIdByIdempotencyKey:idem-1",
        "insertTask:7:learn monotonic stack:system prompt",
        "insertTurn:101",
        "insertUserMessage:101:201:learn monotonic stack:5",
        "insertRun:101:201:idem-1:4",
        "attachTurnUserMessageAndRun:201:301:401");
  }

  @Test
  void reusesExistingRunForIdempotencyKey() {
    mapper.existingRunId = 401L;
    mapper.existingRunRecord = new AgentRunRecord(
        401L,
        101L,
        201L,
        "run-uuid-401",
        "idem-1",
        "system prompt");

    PreparedAgentRun run = repository.createOrReuseRun(new AgentRunPreparationRequest(
        null,
        7L,
        "learn monotonic stack",
        "idem-1",
        "system prompt",
        Map.of("ignored", true)));

    assertThat(run.taskId()).isEqualTo(101);
    assertThat(run.turnId()).isEqualTo(201);
    assertThat(run.runId()).isEqualTo(401);
    assertThat(run.runUuid()).isEqualTo("run-uuid-401");
    assertThat(run.metadata()).containsEntry("idempotentReplay", true);
    assertThat(mapper.calls).containsExactly(
        "lockIdempotencyKey:idem-1",
        "findRunIdByIdempotencyKey:idem-1",
        "findRunRecord:401");
  }

  @Test
  void findsExistingRunByIdempotencyKeyWithoutPreparingNewRun() {
    mapper.existingRunId = 401L;
    mapper.existingRunRecord = new AgentRunRecord(
        401L,
        101L,
        201L,
        "run-uuid-401",
        "idem-1",
        "system prompt");

    PreparedAgentRun run = repository.findRunByIdempotencyKey("idem-1").orElseThrow();

    assertThat(run.runId()).isEqualTo(401);
    assertThat(run.metadata()).containsEntry("idempotentReplay", true);
    assertThat(mapper.calls).containsExactly(
        "findRunIdByIdempotencyKey:idem-1",
        "findRunRecord:401");
  }

  @Test
  void recentMessagesReturnsChronologicalMessages() {
    mapper.recentMessages = List.of(
        new AgentMessage(2L, 101L, 2L, AgentMessage.Role.ASSISTANT, "answer", Instant.parse("2026-01-01T00:01:00Z")),
        new AgentMessage(1L, 101L, 1L, AgentMessage.Role.USER, "question", Instant.parse("2026-01-01T00:00:00Z")));

    List<AgentMessage> messages = repository.recentMessages(101L, 3);

    assertThat(messages).extracting(AgentMessage::sequenceNo).containsExactly(1L, 2L);
    assertThat(mapper.calls).containsExactly("recentMessages:101:3");
  }

  @Test
  void passesUserMessageMetadataWhenPreparingRun() {
    FakeConversationMapper mapper = new FakeConversationMapper();
    PostgresAgentConversationRepository repository = new PostgresAgentConversationRepository(mapper);

    repository.createOrReuseRun(new AgentRunPreparationRequest(
        10L,
        42L,
        "hello",
        "idem-meta",
        "system",
        Map.of(),
        Map.of("messageType", "CHAT")));

    assertThat(mapper.lastUserMessageMetadata).containsEntry("messageType", "CHAT");
  }

  @Test
  void createsAssistantSeedMessageWithMetadata() {
    FakeConversationMapper mapper = new FakeConversationMapper();
    PostgresAgentConversationRepository repository = new PostgresAgentConversationRepository(mapper);
    mapper.nextTurnId = 20L;
    mapper.nextMessageId = 30L;
    Instant persistedAt = Instant.parse("2026-01-01T00:03:00Z");
    mapper.messageById = new AgentMessage(
        30L,
        10L,
        7L,
        AgentMessage.Role.ASSISTANT,
        "# Two Sum",
        persistedAt,
        Map.of("messageType", "PROBLEM_STATEMENT"));

    AgentMessage message = repository.createAssistantSeedMessage(new AgentAssistantSeedMessageRequest(
        10L,
        "# Two Sum",
        Map.of("messageType", "PROBLEM_STATEMENT")));

    assertThat(message.id()).isEqualTo(30L);
    assertThat(message.role()).isEqualTo(AgentMessage.Role.ASSISTANT);
    assertThat(message.sequenceNo()).isEqualTo(7L);
    assertThat(message.createdAt()).isEqualTo(persistedAt);
    assertThat(message.metadata()).containsEntry("messageType", "PROBLEM_STATEMENT");
    assertThat(mapper.calls).containsExactly(
        "insertTurn:10",
        "insertAssistantSeedMessage:10:# Two Sum",
        "attachTurnAssistantSeedMessage:20:30",
        "findMessageById:30");
  }

  @Test
  void returnsActiveRunForTask() {
    mapper.activeRun = new AgentActiveRun(
        401L,
        101L,
        "run-uuid-401",
        "idem-401",
        Instant.parse("2026-01-01T00:04:00Z"));

    AgentActiveRun run = repository.activeRun(101L).orElseThrow();

    assertThat(run.runUuid()).isEqualTo("run-uuid-401");
    assertThat(mapper.calls).containsExactly("findActiveRun:101");
  }

  private static final class FakeConversationMapper implements AgentConversationMapper {
    private final List<String> calls = new ArrayList<>();
    private Long existingRunId;
    private AgentRunRecord existingRunRecord;
    private AgentMessage messageById;
    private AgentActiveRun activeRun;
    private List<AgentMessage> recentMessages = List.of();
    private Map<String, Object> lastUserMessageMetadata = Map.of();
    private Map<String, Object> lastSeedMetadata = Map.of();
    private long nextTaskId = 1L;
    private long nextTurnId = 1L;
    private long nextMessageId = 1L;
    private long nextRunId = 1L;

    @Override
    public int lockIdempotencyKey(String idempotencyKey) {
      calls.add("lockIdempotencyKey:" + idempotencyKey);
      return 1;
    }

    @Override
    public Long findRunIdByIdempotencyKey(String idempotencyKey) {
      calls.add("findRunIdByIdempotencyKey:" + idempotencyKey);
      return existingRunId;
    }

    @Override
    public AgentRunRecord findRunRecord(long runId) {
      calls.add("findRunRecord:" + runId);
      return existingRunRecord;
    }

    @Override
    public AgentTurnMessagesRow findTurnMessagesByRunId(long runId) {
      calls.add("findTurnMessagesByRunId:" + runId);
      return null;
    }

    @Override
    public long insertTask(Long userId, String title, String systemPrompt, Map<String, Object> metadata) {
      calls.add("insertTask:" + userId + ":" + title + ":" + systemPrompt);
      return nextTaskId;
    }

    @Override
    public long insertTurn(long taskId) {
      calls.add("insertTurn:" + taskId);
      return nextTurnId;
    }

    @Override
    public long insertUserMessage(
        long taskId,
        long turnId,
        String content,
        int tokenEstimate,
        Map<String, Object> metadata
    ) {
      calls.add("insertUserMessage:" + taskId + ":" + turnId + ":" + content + ":" + tokenEstimate);
      lastUserMessageMetadata = metadata;
      return nextMessageId;
    }

    @Override
    public long insertAssistantSeedMessage(
        long taskId,
        long turnId,
        String content,
        int tokenEstimate,
        Map<String, Object> metadata
    ) {
      calls.add("insertAssistantSeedMessage:" + taskId + ":" + content);
      lastSeedMetadata = metadata;
      return nextMessageId;
    }

    @Override
    public long insertRun(long taskId, long turnId, String runUuid, String idempotencyKey, int maxSteps) {
      calls.add("insertRun:" + taskId + ":" + turnId + ":" + idempotencyKey + ":" + maxSteps);
      return nextRunId;
    }

    @Override
    public int attachTurnUserMessageAndRun(long turnId, long userMessageId, long runId) {
      calls.add("attachTurnUserMessageAndRun:" + turnId + ":" + userMessageId + ":" + runId);
      return 1;
    }

    @Override
    public int attachTurnAssistantSeedMessage(long turnId, long assistantMessageId) {
      calls.add("attachTurnAssistantSeedMessage:" + turnId + ":" + assistantMessageId);
      return 1;
    }

    @Override
    public List<AgentMessage> recentMessages(long taskId, int messageLimit) {
      calls.add("recentMessages:" + taskId + ":" + messageLimit);
      return recentMessages;
    }

    @Override
    public List<AgentMessage> messages(long taskId, int messageLimit) {
      calls.add("messages:" + taskId + ":" + messageLimit);
      return List.of();
    }

    @Override
    public AgentMessage findMessageById(long messageId) {
      calls.add("findMessageById:" + messageId);
      return messageById;
    }

    @Override
    public AgentActiveRun findActiveRun(long taskId) {
      calls.add("findActiveRun:" + taskId);
      return activeRun;
    }
  }
}
