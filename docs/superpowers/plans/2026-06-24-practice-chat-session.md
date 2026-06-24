# Practice Chat Session Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the complete practice chat product loop: create or reuse a practice session from a learning-plan problem, show the deterministic problem-statement seed message, stream AI chat replies through the existing Agent runtime and Prompt Assembly, and update problem progress.

**Architecture:** `practice_session` is the product chat thread, `learning_plan_problem_progress` is the progress fact table, and `agent_task` remains the Agent runtime thread. The practice application service owns product validation and response mapping, while Agent runtime repositories own all `agent_*` SQL. The frontend talks only to practice-specific APIs and renders persisted messages plus SSE deltas.

**Tech Stack:** Java 17, Spring MVC, Maven, MyBatis, PostgreSQL/Flyway, Agent runtime, Prompt Assembly, AI governance, React 19, TypeScript, Vite, Vitest/React Testing Library.

---

## Reference Specs

- `docs/practice-chat-agent-design.md`
- `docs/practice-chat-system-prompt-assembly-design.md`
- `docs/practice-chat-workbench-design.md`
- `docs/code-index.md`

## File Structure

### Agent runtime

- Modify `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/runtime/repository/AgentConversationRepository.java` to pass metadata into user messages.
- Create `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/runtime/model/AgentTaskRef.java` for task creation results.
- Create `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/runtime/model/AgentTaskCreationRequest.java` for product-owned task creation.
- Create `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/runtime/model/AgentAssistantSeedMessageRequest.java` for deterministic assistant seed messages.
- Create `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/runtime/repository/AgentTaskMessageRepository.java` for task and message operations that are not run preparation.
- Modify `backend/agent-persistence-postgres/src/main/java/org/congcong/algomentor/agent/persistence/postgres/repository/PostgresAgentConversationRepository.java` to implement `AgentTaskMessageRepository` and write metadata.
- Modify `backend/agent-persistence-postgres/src/main/java/org/congcong/algomentor/agent/persistence/postgres/mapper/AgentConversationMapper.java` and `backend/agent-persistence-postgres/src/main/resources/mapper/agent/AgentConversationMapper.xml` to insert task metadata, user message metadata, assistant seed messages, and message lists.
- Modify `backend/agent-persistence-postgres/src/main/java/org/congcong/algomentor/agent/persistence/postgres/mapper/AgentRunMapper.java` and `backend/agent-persistence-postgres/src/main/resources/mapper/agent/AgentRunMapper.xml` to persist assistant message metadata from the final Agent output.
- Modify `backend/agent-persistence-postgres/src/main/java/org/congcong/algomentor/agent/persistence/postgres/observer/PersistentAgentRunObserver.java` to derive assistant `messageType=CHAT` metadata from `AgentRequest.metadata()`.

### Practice application

- Create `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeProgressStatus.java`.
- Create `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeSessionStatus.java`.
- Create `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeProgress.java`.
- Create `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeSession.java`.
- Create `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeSessionMessage.java`.
- Create `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeSessionResult.java`.
- Create `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeSessionRepository.java`.
- Create `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeSessionService.java` for create/get/update progress.
- Create `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeMessageStreamService.java` for session-owned streaming preparation.
- Modify `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeChatPromptConstants.java` to add `practiceSessionId` metadata key.

### Practice API and persistence

- Create `backend/mentor-api/src/main/resources/db/migration/V12__practice_session_schema.sql`.
- Create `backend/mentor-api/src/main/java/org/congcong/algomentor/api/practice/mapper/PracticeSessionMapper.java`.
- Create `backend/mentor-api/src/main/java/org/congcong/algomentor/api/practice/mapper/model/PracticeProgressRow.java`.
- Create `backend/mentor-api/src/main/java/org/congcong/algomentor/api/practice/mapper/model/PracticeSessionRow.java`.
- Create `backend/mentor-api/src/main/resources/mapper/practice/PracticeSessionMapper.xml`.
- Create `backend/mentor-api/src/main/java/org/congcong/algomentor/api/practice/repository/MyBatisPracticeSessionRepository.java`.
- Create `backend/mentor-api/src/main/java/org/congcong/algomentor/api/practice/model/PracticeSessionResponse.java`.
- Create `backend/mentor-api/src/main/java/org/congcong/algomentor/api/practice/model/PracticeSessionSummaryResponse.java`.
- Create `backend/mentor-api/src/main/java/org/congcong/algomentor/api/practice/model/PracticeProblemSummaryResponse.java`.
- Create `backend/mentor-api/src/main/java/org/congcong/algomentor/api/practice/model/PracticeMessageResponse.java`.
- Create `backend/mentor-api/src/main/java/org/congcong/algomentor/api/practice/model/PracticeProgressStatusRequest.java`.
- Create `backend/mentor-api/src/main/java/org/congcong/algomentor/api/practice/model/PracticeMessageRequest.java`.
- Create `backend/mentor-api/src/main/java/org/congcong/algomentor/api/practice/model/PracticeSessionResponseMapper.java`.
- Create `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/practice/PracticeSessionController.java`.
- Create `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/practice/PracticeSessionExceptionHandler.java`.
- Modify `backend/mentor-api/src/main/java/org/congcong/algomentor/api/config/ApiContractConstants.java` to add practice paths.
- Modify `backend/mentor-api/src/main/java/org/congcong/algomentor/mentor/api/autoconfigure/AgentConversationApiAutoConfiguration.java` to wire practice services and controller.
- Modify `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/model/AiRunSource.java` to add `PRACTICE_CHAT`.

### Frontend

- Modify `frontend/src/types/api.ts` to add practice session, message, and progress types.
- Modify `frontend/src/services/api.ts` to add practice API functions and include the `practice` field in generic compaction only if generic debug use still needs it.
- Modify `frontend/src/learning-plans/PracticeChatWorkbench.tsx` to call practice APIs, render persisted messages, stream assistant deltas, and update progress.
- Modify locale files under `frontend/src/i18n/locales.ts` only for new visible copy that does not already exist.
- Modify frontend tests in `frontend/src/App.test.tsx` or create `frontend/src/learning-plans/PracticeChatWorkbench.test.tsx` based on existing test locality.

---

### Task 1: Agent Runtime Metadata and Task/Message Port

**Files:**
- Create: `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/runtime/model/AgentTaskRef.java`
- Create: `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/runtime/model/AgentTaskCreationRequest.java`
- Create: `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/runtime/model/AgentAssistantSeedMessageRequest.java`
- Create: `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/runtime/repository/AgentTaskMessageRepository.java`
- Modify: `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/runtime/model/AgentRunPreparationRequest.java`
- Modify: `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/runtime/repository/AgentConversationRepository.java`
- Test: `backend/agent-core/src/test/java/org/congcong/algomentor/agent/core/runtime/model/AgentTaskMessageRequestTest.java`
- Test: `backend/mentor-application/src/test/java/org/congcong/algomentor/mentor/application/conversation/AgentConversationServiceTest.java`

- [ ] **Step 1: Write failing model tests**

Create `backend/agent-core/src/test/java/org/congcong/algomentor/agent/core/runtime/model/AgentTaskMessageRequestTest.java`:

```java
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
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl agent-core -Dtest=AgentTaskMessageRequestTest test
```

Expected: compilation fails because the new records and `userMessageMetadata()` do not exist.

- [ ] **Step 3: Add runtime request models and repository port**

Add `AgentTaskRef.java`:

```java
package org.congcong.algomentor.agent.core.runtime.model;

public record AgentTaskRef(long taskId) {

  public AgentTaskRef {
    if (taskId < 1) {
      throw new IllegalArgumentException("Agent task id must be positive");
    }
  }
}
```

Add `AgentTaskCreationRequest.java`:

```java
package org.congcong.algomentor.agent.core.runtime.model;

import java.util.Map;

public record AgentTaskCreationRequest(
    Long userId,
    String title,
    String systemPrompt,
    Map<String, Object> metadata
) {

  public AgentTaskCreationRequest {
    if (userId != null && userId < 1) {
      throw new IllegalArgumentException("Agent task user id must be positive");
    }
    title = title == null || title.isBlank() ? "practice-session" : title.trim();
    systemPrompt = systemPrompt == null ? "" : systemPrompt;
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }
}
```

Add `AgentAssistantSeedMessageRequest.java`:

```java
package org.congcong.algomentor.agent.core.runtime.model;

import java.util.Map;

public record AgentAssistantSeedMessageRequest(
    long taskId,
    String content,
    Map<String, Object> metadata
) {

  public AgentAssistantSeedMessageRequest {
    if (taskId < 1) {
      throw new IllegalArgumentException("Agent seed message task id must be positive");
    }
    if (content == null || content.isBlank()) {
      throw new IllegalArgumentException("Agent seed message content must not be blank");
    }
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }
}
```

Add `AgentTaskMessageRepository.java`:

```java
package org.congcong.algomentor.agent.core.runtime.repository;

import java.util.List;
import org.congcong.algomentor.agent.core.runtime.model.AgentAssistantSeedMessageRequest;
import org.congcong.algomentor.agent.core.runtime.model.AgentMessage;
import org.congcong.algomentor.agent.core.runtime.model.AgentTaskCreationRequest;
import org.congcong.algomentor.agent.core.runtime.model.AgentTaskRef;

public interface AgentTaskMessageRepository {

  AgentTaskRef createTask(AgentTaskCreationRequest request);

  AgentMessage createAssistantSeedMessage(AgentAssistantSeedMessageRequest request);

  List<AgentMessage> messages(long taskId, int messageLimit);
}
```

- [ ] **Step 4: Extend `AgentRunPreparationRequest` with user message metadata**

Modify `AgentRunPreparationRequest.java` so the record is:

```java
public record AgentRunPreparationRequest(
    Long taskId,
    Long userId,
    String userMessage,
    String idempotencyKey,
    String systemPrompt,
    Map<String, Object> metadata,
    Map<String, Object> userMessageMetadata
) {
```

Add this compatibility constructor below the record header:

```java
  public AgentRunPreparationRequest(
      Long taskId,
      Long userId,
      String userMessage,
      String idempotencyKey,
      String systemPrompt,
      Map<String, Object> metadata
  ) {
    this(taskId, userId, userMessage, idempotencyKey, systemPrompt, metadata, Map.of());
  }
```

Add this line to the compact constructor:

```java
    userMessageMetadata = userMessageMetadata == null ? Map.of() : Map.copyOf(userMessageMetadata);
```

- [ ] **Step 5: Run model tests**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl agent-core -Dtest=AgentTaskMessageRequestTest test
```

Expected: PASS.

- [ ] **Step 6: Update application test expectation for user message metadata**

In `AgentConversationServiceTest.CapturingRepository`, keep `lastRequest` as-is and add an assertion to `preparesPracticeChatRunWithPromptAssemblyAndFiltersProblemStatementHistory`:

```java
    assertThat(repository.lastRequest.userMessageMetadata())
        .containsEntry(PracticeChatPromptConstants.MESSAGE_TYPE_METADATA_KEY, PracticeChatPromptConstants.MESSAGE_TYPE_CHAT)
        .containsEntry(PracticeChatPromptConstants.METADATA_SCENARIO, PracticeChatPromptConstants.SCENARIO);
```

- [ ] **Step 7: Run application test to verify it fails**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-application -am -Dtest=AgentConversationServiceTest test
```

Expected: FAIL because `AgentConversationService` does not yet set user message metadata.

- [ ] **Step 8: Add user message metadata in `AgentConversationService`**

Modify `toPreparationRequest` in `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/conversation/AgentConversationService.java` to call the new constructor:

```java
    return new AgentRunPreparationRequest(
        command.taskId(),
        command.userId(),
        command.userMessage(),
        command.idempotencyKey(),
        DEFAULT_MENTOR_SYSTEM_PROMPT,
        preparationMetadata(command),
        userMessageMetadata(command));
```

Add this helper method:

```java
  private Map<String, Object> userMessageMetadata(AgentConversationCommand command) {
    if (!command.practiceChatEnabled()) {
      return Map.of();
    }
    PracticeChatReference reference = command.practiceChat();
    Map<String, Object> metadata = new HashMap<>();
    metadata.put(PracticeChatPromptConstants.MESSAGE_TYPE_METADATA_KEY, PracticeChatPromptConstants.MESSAGE_TYPE_CHAT);
    metadata.put(PracticeChatPromptConstants.METADATA_SCENARIO, PracticeChatPromptConstants.SCENARIO);
    metadata.put(PracticeChatPromptConstants.METADATA_PLAN_ID, reference.planId());
    metadata.put(PracticeChatPromptConstants.METADATA_PHASE_INDEX, reference.phaseIndex());
    metadata.put(PracticeChatPromptConstants.METADATA_PROBLEM_SLUG, reference.problemSlug());
    metadata.put(PracticeChatPromptConstants.METADATA_LOCALE, reference.locale());
    return Map.copyOf(metadata);
  }
```

- [ ] **Step 9: Run tests and commit**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl agent-core,mentor-application -am -Dtest=AgentTaskMessageRequestTest,AgentConversationServiceTest test
```

Expected: PASS.

Commit:

```bash
git add backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/runtime/model \
  backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/runtime/repository \
  backend/agent-core/src/test/java/org/congcong/algomentor/agent/core/runtime/model \
  backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/conversation/AgentConversationService.java \
  backend/mentor-application/src/test/java/org/congcong/algomentor/mentor/application/conversation/AgentConversationServiceTest.java
git commit -m "feat: add agent task message metadata port"
```

---

### Task 2: PostgreSQL Agent Runtime Support

**Files:**
- Modify: `backend/agent-persistence-postgres/src/main/java/org/congcong/algomentor/agent/persistence/postgres/repository/PostgresAgentConversationRepository.java`
- Modify: `backend/agent-persistence-postgres/src/main/java/org/congcong/algomentor/agent/persistence/postgres/mapper/AgentConversationMapper.java`
- Modify: `backend/agent-persistence-postgres/src/main/resources/mapper/agent/AgentConversationMapper.xml`
- Modify: `backend/agent-persistence-postgres/src/main/java/org/congcong/algomentor/agent/persistence/postgres/mapper/AgentRunMapper.java`
- Modify: `backend/agent-persistence-postgres/src/main/resources/mapper/agent/AgentRunMapper.xml`
- Modify: `backend/agent-persistence-postgres/src/main/java/org/congcong/algomentor/agent/persistence/postgres/observer/PersistentAgentRunObserver.java`
- Test: `backend/agent-persistence-postgres/src/test/java/org/congcong/algomentor/agent/persistence/postgres/repository/PostgresAgentConversationRepositoryTest.java`
- Test: `backend/agent-persistence-postgres/src/test/java/org/congcong/algomentor/agent/persistence/postgres/observer/PersistentAgentRunObserverTest.java`

- [ ] **Step 1: Add failing repository tests for metadata and seed messages**

In `PostgresAgentConversationRepositoryTest`, add tests using the existing fake mapper pattern:

```java
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

    AgentMessage message = repository.createAssistantSeedMessage(new AgentAssistantSeedMessageRequest(
        10L,
        "# Two Sum",
        Map.of("messageType", "PROBLEM_STATEMENT")));

    assertThat(message.role()).isEqualTo(AgentMessage.Role.ASSISTANT);
    assertThat(message.metadata()).containsEntry("messageType", "PROBLEM_STATEMENT");
    assertThat(mapper.calls).contains("insertAssistantSeedMessage:10:# Two Sum");
  }
```

Add fields to the fake mapper:

```java
    Map<String, Object> lastUserMessageMetadata = Map.of();
    Map<String, Object> lastSeedMetadata = Map.of();
```

- [ ] **Step 2: Run repository test to verify it fails**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl agent-persistence-postgres -am -Dtest=PostgresAgentConversationRepositoryTest test
```

Expected: compilation fails because the mapper and repository methods do not support metadata/seed creation yet.

- [ ] **Step 3: Implement mapper signatures**

Modify `AgentConversationMapper.java`:

```java
  long insertTask(
      @Param("userId") Long userId,
      @Param("title") String title,
      @Param("systemPrompt") String systemPrompt,
      @Param("metadata") Map<String, Object> metadata
  );
```

```java
  long insertUserMessage(
      @Param("taskId") long taskId,
      @Param("turnId") long turnId,
      @Param("content") String content,
      @Param("tokenEstimate") int tokenEstimate,
      @Param("metadata") Map<String, Object> metadata
  );
```

Add:

```java
  long insertAssistantSeedMessage(
      @Param("taskId") long taskId,
      @Param("turnId") long turnId,
      @Param("content") String content,
      @Param("tokenEstimate") int tokenEstimate,
      @Param("metadata") Map<String, Object> metadata
  );

  List<AgentMessage> messages(
      @Param("taskId") long taskId,
      @Param("messageLimit") int messageLimit
  );
```

- [ ] **Step 4: Implement XML SQL changes**

In `AgentConversationMapper.xml`, add `metadata` to `insertTask`:

```xml
  <select id="insertTask" resultType="_long">
    INSERT INTO agent_task (user_id, title, status, system_prompt, metadata, created_at, updated_at)
    VALUES (
      #{userId},
      #{title},
      'active',
      #{systemPrompt},
      #{metadata,jdbcType=OTHER,typeHandler=org.congcong.algomentor.agent.persistence.postgres.json.JsonbMapTypeHandler},
      NOW(),
      NOW()
    )
    RETURNING id
  </select>
```

Add `metadata` to `insertUserMessage` columns and values:

```xml
      metadata,
```

```xml
      #{metadata,jdbcType=OTHER,typeHandler=org.congcong.algomentor.agent.persistence.postgres.json.JsonbMapTypeHandler},
```

Add seed insertion:

```xml
  <select id="insertAssistantSeedMessage" resultType="_long">
    INSERT INTO agent_message (
      task_id,
      turn_id,
      role,
      content,
      sequence_no,
      status,
      token_estimate,
      metadata,
      created_at,
      updated_at
    )
    VALUES (
      #{taskId},
      #{turnId},
      'assistant',
      #{content},
      DEFAULT,
      'active',
      #{tokenEstimate},
      #{metadata,jdbcType=OTHER,typeHandler=org.congcong.algomentor.agent.persistence.postgres.json.JsonbMapTypeHandler},
      NOW(),
      NOW()
    )
    RETURNING id
  </select>
```

Add message listing:

```xml
  <select id="messages" resultMap="AgentMessageMap">
    SELECT id, task_id, sequence_no, role, content, created_at, metadata
    FROM agent_message
    WHERE task_id = #{taskId}
      AND status = 'active'
    ORDER BY sequence_no DESC
    LIMIT #{messageLimit}
  </select>
```

- [ ] **Step 5: Implement repository interface**

Update `PostgresAgentConversationRepository` class declaration:

```java
public class PostgresAgentConversationRepository implements AgentConversationRepository, AgentTaskMessageRepository {
```

Update existing calls:

```java
    long userMessageId = conversationMapper.insertUserMessage(
        taskId,
        turnId,
        request.userMessage(),
        estimateTokens(request.userMessage()),
        request.userMessageMetadata());
```

```java
    return conversationMapper.insertTask(
        request.userId(),
        title(request.userMessage()),
        request.systemPrompt(),
        request.metadata());
```

Add methods:

```java
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
    return conversationMapper.messages(request.taskId(), 1).stream()
        .filter(message -> message.id() == messageId)
        .findFirst()
        .orElse(new AgentMessage(
            messageId,
            request.taskId(),
            1L,
            AgentMessage.Role.ASSISTANT,
            request.content(),
            java.time.Instant.EPOCH,
            request.metadata()));
  }

  @Override
  public List<AgentMessage> messages(long taskId, int messageLimit) {
    return conversationMapper.messages(taskId, messageLimit).stream()
        .sorted(Comparator.comparingLong(AgentMessage::sequenceNo))
        .toList();
  }
```

Add a dedicated mapper method for deterministic assistant seed turns:

```java
  int attachTurnAssistantSeedMessage(
      @Param("turnId") long turnId,
      @Param("assistantMessageId") long assistantMessageId
  );
```

and SQL:

```xml
  <update id="attachTurnAssistantSeedMessage">
    UPDATE agent_turn
    SET assistant_message_id = #{assistantMessageId},
        status = 'succeeded',
        updated_at = NOW()
    WHERE id = #{turnId}
  </update>
```

Use this dedicated method from `createAssistantSeedMessage`.

- [ ] **Step 6: Add assistant output metadata persistence test**

In `PersistentAgentRunObserverTest`, add:

```java
  @Test
  void writesAssistantMessageMetadataFromRequestMetadata() {
    FakeRunMapper mapper = new FakeRunMapper();
    PersistentAgentRunObserver observer = new PersistentAgentRunObserver(mapper, new ObjectMapper(), fixedClock());
    AgentRequest request = new AgentRequest(
        "run-1",
        "idem-1",
        List.of(LlmMessage.user("hello")),
        Map.of(
            AgentRuntimeMetadataKeys.TASK_ID, 10L,
            AgentRuntimeMetadataKeys.TURN_ID, 20L,
            AgentRuntimeMetadataKeys.RUN_DB_ID, 30L,
            "scenario", "PRACTICE_CHAT",
            "messageType", "CHAT",
            "practiceSessionId", 100L,
            "planId", 12L,
            "phaseIndex", 1,
            "problemSlug", "two-sum"));

    AgentLoopContext context = new AgentLoopContext("run-1", request, 4, request.metadata(), null);
    observer.onRunStart(context);
    observer.onFinalOutput(context, new AgentOutput("answer", Map.of()));

    assertThat(mapper.lastAssistantMetadata)
        .containsEntry("messageType", "CHAT")
        .containsEntry("scenario", "PRACTICE_CHAT")
        .containsEntry("practiceSessionId", 100L);
  }
```

- [ ] **Step 7: Update `AgentRunMapper` and observer**

Modify `AgentRunMapper.insertAssistantMessage` signature to include:

```java
      @Param("metadata") Map<String, Object> metadata,
```

Add `metadata` column/value to `AgentRunMapper.xml`:

```xml
      metadata,
```

```xml
      #{metadata,jdbcType=OTHER,typeHandler=org.congcong.algomentor.agent.persistence.postgres.json.JsonbMapTypeHandler},
```

In `PersistentAgentRunObserver.onFinalOutput`, pass `assistantMessageMetadata(context)`:

```java
        assistantMessageMetadata(context),
        now,
        now);
```

Add helper:

```java
  private Map<String, Object> assistantMessageMetadata(AgentLoopContext context) {
    Object scenario = context.metadata().get("scenario");
    Object messageType = context.metadata().get("messageType");
    if (!"PRACTICE_CHAT".equals(scenario) || messageType == null) {
      return Map.of();
    }
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("messageType", messageType);
    copyMetadata(context, metadata, "scenario");
    copyMetadata(context, metadata, "practiceSessionId");
    copyMetadata(context, metadata, "planId");
    copyMetadata(context, metadata, "phaseIndex");
    copyMetadata(context, metadata, "problemSlug");
    return Map.copyOf(metadata);
  }

  private void copyMetadata(AgentLoopContext context, Map<String, Object> target, String key) {
    Object value = context.metadata().get(key);
    if (value != null) {
      target.put(key, value);
    }
  }
```

- [ ] **Step 8: Run persistence tests and commit**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl agent-persistence-postgres -am -Dtest=PostgresAgentConversationRepositoryTest,PersistentAgentRunObserverTest test
```

Expected: PASS.

Commit:

```bash
git add backend/agent-persistence-postgres/src/main/java backend/agent-persistence-postgres/src/main/resources/mapper/agent backend/agent-persistence-postgres/src/test/java
git commit -m "feat: persist agent message metadata"
```

---

### Task 3: Practice Domain Models and Service Tests

**Files:**
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeProgressStatus.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeSessionStatus.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeProgress.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeSession.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeSessionMessage.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeSessionResult.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeSessionRepository.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeSessionService.java`
- Modify: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeChatPromptConstants.java`
- Test: `backend/mentor-application/src/test/java/org/congcong/algomentor/mentor/application/practice/PracticeSessionServiceTest.java`

- [ ] **Step 1: Write failing service tests**

Create `PracticeSessionServiceTest.java` with three tests:

```java
package org.congcong.algomentor.mentor.application.practice;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.congcong.algomentor.agent.core.runtime.model.AgentMessage;
import org.congcong.algomentor.agent.core.runtime.model.AgentTaskRef;
import org.congcong.algomentor.agent.core.runtime.repository.AgentTaskMessageRepository;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDifficultyPreference;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanIntent;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLevel;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanPhaseDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanRepository;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanStatus;
import org.junit.jupiter.api.Test;

class PracticeSessionServiceTest {

  @Test
  void createsSessionTaskSeedAndProgress() {
    InMemoryPracticeSessionRepository sessions = new InMemoryPracticeSessionRepository();
    InMemoryAgentTaskMessageRepository messages = new InMemoryAgentTaskMessageRepository();
    PracticeSessionService service = new PracticeSessionService(
        new InMemoryPlanRepository(plan()),
        new FakeProblemCatalog(),
        sessions,
        messages);

    PracticeSessionResult result = service.createOrReuse(7L, new PracticeChatReference(12L, 1, "two-sum", "zh-CN"));

    assertThat(result.session().progressStatus()).isEqualTo(PracticeProgressStatus.IN_PROGRESS);
    assertThat(result.session().agentTaskId()).isEqualTo(100L);
    assertThat(result.messages()).hasSize(1);
    assertThat(result.messages().get(0).messageType()).isEqualTo(PracticeChatPromptConstants.MESSAGE_TYPE_PROBLEM_STATEMENT);
    assertThat(result.messages().get(0).contentMarkdown()).contains("# Two Sum");
    assertThat(messages.seedRequests).hasSize(1);
  }

  @Test
  void reusesExistingSeedWithoutCreatingAnotherMessage() {
    InMemoryPracticeSessionRepository sessions = new InMemoryPracticeSessionRepository();
    InMemoryAgentTaskMessageRepository messages = new InMemoryAgentTaskMessageRepository();
    PracticeSessionService service = new PracticeSessionService(
        new InMemoryPlanRepository(plan()),
        new FakeProblemCatalog(),
        sessions,
        messages);

    service.createOrReuse(7L, new PracticeChatReference(12L, 1, "two-sum", "zh-CN"));
    PracticeSessionResult second = service.createOrReuse(7L, new PracticeChatReference(12L, 1, "two-sum", "zh-CN"));

    assertThat(second.session().agentTaskId()).isEqualTo(100L);
    assertThat(messages.seedRequests).hasSize(1);
  }

  @Test
  void completedProgressDoesNotReturnToInProgress() {
    InMemoryPracticeSessionRepository sessions = new InMemoryPracticeSessionRepository();
    sessions.nextProgressStatus = PracticeProgressStatus.COMPLETED;
    PracticeSessionService service = new PracticeSessionService(
        new InMemoryPlanRepository(plan()),
        new FakeProblemCatalog(),
        sessions,
        new InMemoryAgentTaskMessageRepository());

    PracticeSessionResult result = service.createOrReuse(7L, new PracticeChatReference(12L, 1, "two-sum", "zh-CN"));

    assertThat(result.session().progressStatus()).isEqualTo(PracticeProgressStatus.COMPLETED);
  }
}
```

Define these private in-memory fakes in the same test class:

- `InMemoryPracticeSessionRepository` stores one `PracticeSession`, returns `sessionId=50`, starts with `agentTaskId=null`, and after `attachAgentTask` returns `agentTaskId=100`.
- `InMemoryAgentTaskMessageRepository` returns `new AgentTaskRef(100)`, records every seed request in `seedRequests`, returns a seed `AgentMessage` with `id=200`, and returns all persisted fake messages from `messages(long taskId, int messageLimit)`.
- `InMemoryPlanRepository` returns the same `LearningPlan` fixture shape used in `AgentConversationServiceTest.plan()`: plan id `12`, user id `7`, phase index `1`, problem slug `two-sum`.
- `FakeProblemCatalog` returns `PracticeChatProblemDetail("two-sum", 1, "Two Sum", "EASY", List.of("Array", "Hash Table"), "# Two Sum", "https://leetcode.com/problems/two-sum/")`.

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-application -am -Dtest=PracticeSessionServiceTest test
```

Expected: compilation fails because practice session models and service do not exist.

- [ ] **Step 3: Add statuses and records**

Create the enum files:

```java
package org.congcong.algomentor.mentor.application.practice;

public enum PracticeProgressStatus {
  NOT_STARTED,
  IN_PROGRESS,
  COMPLETED,
  SKIPPED
}
```

```java
package org.congcong.algomentor.mentor.application.practice;

public enum PracticeSessionStatus {
  ACTIVE,
  ARCHIVED
}
```

Create `PracticeProgress.java`:

```java
package org.congcong.algomentor.mentor.application.practice;

import java.time.Instant;

public record PracticeProgress(
    long id,
    long userId,
    long planId,
    int phaseIndex,
    String problemSlug,
    PracticeProgressStatus status,
    Instant createdAt,
    Instant updatedAt
) {
}
```

Create `PracticeSession.java`:

```java
package org.congcong.algomentor.mentor.application.practice;

import java.time.Instant;

public record PracticeSession(
    long id,
    long userId,
    long planId,
    int phaseIndex,
    String problemSlug,
    PracticeSessionStatus status,
    Long agentTaskId,
    Long problemStatementMessageId,
    PracticeProgressStatus progressStatus,
    Instant lastMessageAt,
    Instant createdAt,
    Instant updatedAt
) {

  public PracticeSession withAgentTaskId(long taskId) {
    return new PracticeSession(
        id, userId, planId, phaseIndex, problemSlug, status, taskId,
        problemStatementMessageId, progressStatus, lastMessageAt, createdAt, updatedAt);
  }

  public PracticeSession withProblemStatementMessageId(long messageId) {
    return new PracticeSession(
        id, userId, planId, phaseIndex, problemSlug, status, agentTaskId,
        messageId, progressStatus, lastMessageAt, createdAt, updatedAt);
  }
}
```

Create `PracticeSessionMessage.java`:

```java
package org.congcong.algomentor.mentor.application.practice;

import java.time.Instant;

public record PracticeSessionMessage(
    long id,
    String role,
    String messageType,
    String contentMarkdown,
    Instant createdAt
) {
}
```

Create `PracticeSessionResult.java`:

```java
package org.congcong.algomentor.mentor.application.practice;

import java.util.List;

public record PracticeSessionResult(
    PracticeSession session,
    PracticeChatProblemDetail problem,
    List<PracticeSessionMessage> messages
) {

  public PracticeSessionResult {
    messages = messages == null ? List.of() : List.copyOf(messages);
  }
}
```

- [ ] **Step 4: Add repository interface and constant**

Add `PracticeSessionRepository.java`:

```java
package org.congcong.algomentor.mentor.application.practice;

import java.util.Optional;

public interface PracticeSessionRepository {

  PracticeProgress upsertAndAdvanceProgress(long userId, long planId, int phaseIndex, String problemSlug);

  PracticeSession upsertAndLockSession(long userId, long planId, int phaseIndex, String problemSlug);

  Optional<PracticeSession> findSessionForUser(long sessionId, long userId);

  PracticeSession attachAgentTask(long sessionId, long agentTaskId);

  PracticeSession attachProblemStatementMessage(long sessionId, long messageId);

  PracticeProgress updateProgressStatus(long sessionId, long userId, PracticeProgressStatus status);

  void touchLastMessageAt(long sessionId);
}
```

In `PracticeChatPromptConstants`, add:

```java
  public static final String METADATA_PRACTICE_SESSION_ID = "practiceSessionId";
```

- [ ] **Step 5: Implement `PracticeSessionService`**

Create `PracticeSessionService.java`:

```java
package org.congcong.algomentor.mentor.application.practice;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.congcong.algomentor.agent.core.runtime.model.AgentAssistantSeedMessageRequest;
import org.congcong.algomentor.agent.core.runtime.model.AgentMessage;
import org.congcong.algomentor.agent.core.runtime.model.AgentTaskCreationRequest;
import org.congcong.algomentor.agent.core.runtime.repository.AgentTaskMessageRepository;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanPhaseDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanRepository;

public class PracticeSessionService {

  private static final int MESSAGE_LIMIT = 50;
  private static final String DEFAULT_SYSTEM_PROMPT = "You are an algorithm learning mentor.";

  private final LearningPlanRepository learningPlanRepository;
  private final PracticeChatProblemCatalog problemCatalog;
  private final PracticeSessionRepository sessionRepository;
  private final AgentTaskMessageRepository taskMessageRepository;

  public PracticeSessionService(
      LearningPlanRepository learningPlanRepository,
      PracticeChatProblemCatalog problemCatalog,
      PracticeSessionRepository sessionRepository,
      AgentTaskMessageRepository taskMessageRepository
  ) {
    this.learningPlanRepository = learningPlanRepository;
    this.problemCatalog = problemCatalog;
    this.sessionRepository = sessionRepository;
    this.taskMessageRepository = taskMessageRepository;
  }

  public PracticeSessionResult createOrReuse(long userId, PracticeChatReference reference) {
    PracticeContextParts parts = requireContext(userId, reference);
    PracticeProgress progress = sessionRepository.upsertAndAdvanceProgress(
        userId, reference.planId(), reference.phaseIndex(), reference.problemSlug());
    PracticeSession session = sessionRepository.upsertAndLockSession(
        userId, reference.planId(), reference.phaseIndex(), reference.problemSlug());
    if (session.agentTaskId() == null) {
      long taskId = taskMessageRepository.createTask(new AgentTaskCreationRequest(
          userId,
          taskTitle(parts.planProblem()),
          DEFAULT_SYSTEM_PROMPT,
          metadata(session.id(), reference))).taskId();
      session = sessionRepository.attachAgentTask(session.id(), taskId);
    }
    if (session.problemStatementMessageId() == null) {
      AgentMessage seed = taskMessageRepository.createAssistantSeedMessage(new AgentAssistantSeedMessageRequest(
          session.agentTaskId(),
          seedContent(parts.problemDetail()),
          messageMetadata(session.id(), reference, PracticeChatPromptConstants.MESSAGE_TYPE_PROBLEM_STATEMENT)));
      session = sessionRepository.attachProblemStatementMessage(session.id(), seed.id());
    }
    session = new PracticeSession(
        session.id(), session.userId(), session.planId(), session.phaseIndex(), session.problemSlug(),
        session.status(), session.agentTaskId(), session.problemStatementMessageId(),
        progress.status(), session.lastMessageAt(), session.createdAt(), session.updatedAt());
    return new PracticeSessionResult(
        session,
        parts.problemDetail(),
        messages(session.agentTaskId()));
  }

  public PracticeSessionResult get(long userId, long sessionId) {
    PracticeSession session = sessionRepository.findSessionForUser(sessionId, userId)
        .orElseThrow(() -> new LearningPlanException("PRACTICE_SESSION_NOT_FOUND", "题目训练会话不存在。"));
    PracticeContextParts parts = requireContext(userId, new PracticeChatReference(
        session.planId(), session.phaseIndex(), session.problemSlug(), "zh-CN"));
    return new PracticeSessionResult(session, parts.problemDetail(), messages(session.agentTaskId()));
  }

  public PracticeSession updateProgressStatus(long userId, long sessionId, PracticeProgressStatus status) {
    if (status != PracticeProgressStatus.COMPLETED) {
      throw new LearningPlanException("PRACTICE_PROGRESS_STATUS_UNSUPPORTED", "题目聊天页只支持标记完成。");
    }
    PracticeProgress progress = sessionRepository.updateProgressStatus(sessionId, userId, status);
    PracticeSession session = sessionRepository.findSessionForUser(sessionId, userId)
        .orElseThrow(() -> new LearningPlanException("PRACTICE_SESSION_NOT_FOUND", "题目训练会话不存在。"));
    return new PracticeSession(
        session.id(), session.userId(), session.planId(), session.phaseIndex(), session.problemSlug(),
        session.status(), session.agentTaskId(), session.problemStatementMessageId(),
        progress.status(), session.lastMessageAt(), session.createdAt(), session.updatedAt());
  }

  private PracticeContextParts requireContext(long userId, PracticeChatReference reference) {
    LearningPlan plan = learningPlanRepository.findPlanByIdForUser(reference.planId(), userId)
        .orElseThrow(() -> new LearningPlanException("PRACTICE_CHAT_PLAN_NOT_FOUND", "学习计划不存在。"));
    LearningPlanPhaseDraft phase = plan.plan().phases().stream()
        .filter(candidate -> candidate.phaseIndex() == reference.phaseIndex())
        .findFirst()
        .orElseThrow(() -> new LearningPlanException("PRACTICE_CHAT_PHASE_NOT_FOUND", "学习计划阶段不存在。"));
    LearningPlanProblemDraft planProblem = phase.problems().stream()
        .filter(candidate -> reference.problemSlug().equals(candidate.slug()))
        .findFirst()
        .orElseThrow(() -> new LearningPlanException("PRACTICE_CHAT_PROBLEM_NOT_FOUND", "学习计划题目不存在。"));
    PracticeChatProblemDetail problemDetail = problemCatalog.findProblemBySlug(reference.problemSlug(), reference.locale())
        .orElseThrow(() -> new LearningPlanException("PRACTICE_CHAT_PROBLEM_DETAIL_NOT_FOUND", "题库题目不存在。"));
    return new PracticeContextParts(plan, phase, planProblem, problemDetail);
  }

  private List<PracticeSessionMessage> messages(long taskId) {
    return taskMessageRepository.messages(taskId, MESSAGE_LIMIT).stream()
        .sorted(Comparator.comparingLong(AgentMessage::sequenceNo))
        .map(this::message)
        .toList();
  }

  private PracticeSessionMessage message(AgentMessage message) {
    Object messageType = message.metadata().get(PracticeChatPromptConstants.MESSAGE_TYPE_METADATA_KEY);
    return new PracticeSessionMessage(
        message.id(),
        message.role().name(),
        messageType == null ? PracticeChatPromptConstants.MESSAGE_TYPE_CHAT : String.valueOf(messageType),
        message.content(),
        message.createdAt());
  }

  private String seedContent(PracticeChatProblemDetail detail) {
    return detail.contentMarkdown() == null || detail.contentMarkdown().isBlank()
        ? "题库暂未提供题面 Markdown。"
        : detail.contentMarkdown();
  }

  private String taskTitle(LearningPlanProblemDraft problem) {
    return (problem.frontendId() == null ? "" : problem.frontendId() + ". ") + problem.title();
  }

  private Map<String, Object> metadata(long sessionId, PracticeChatReference reference) {
    return Map.of(
        PracticeChatPromptConstants.METADATA_SCENARIO, PracticeChatPromptConstants.SCENARIO,
        PracticeChatPromptConstants.METADATA_PRACTICE_SESSION_ID, sessionId,
        PracticeChatPromptConstants.METADATA_PLAN_ID, reference.planId(),
        PracticeChatPromptConstants.METADATA_PHASE_INDEX, reference.phaseIndex(),
        PracticeChatPromptConstants.METADATA_PROBLEM_SLUG, reference.problemSlug());
  }

  private Map<String, Object> messageMetadata(long sessionId, PracticeChatReference reference, String messageType) {
    java.util.HashMap<String, Object> values = new java.util.HashMap<>(metadata(sessionId, reference));
    values.put(PracticeChatPromptConstants.MESSAGE_TYPE_METADATA_KEY, messageType);
    return Map.copyOf(values);
  }

  private record PracticeContextParts(
      LearningPlan plan,
      LearningPlanPhaseDraft phase,
      LearningPlanProblemDraft planProblem,
      PracticeChatProblemDetail problemDetail
  ) {
  }
}
```

- [ ] **Step 6: Run service tests and commit**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-application -am -Dtest=PracticeSessionServiceTest,PracticeChatPromptSectionProviderTest test
```

Expected: PASS.

Commit:

```bash
git add backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice backend/mentor-application/src/test/java/org/congcong/algomentor/mentor/application/practice
git commit -m "feat: add practice session service"
```

---

### Task 4: Practice Schema and MyBatis Repository

**Files:**
- Create: `backend/mentor-api/src/main/resources/db/migration/V12__practice_session_schema.sql`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/practice/mapper/PracticeSessionMapper.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/practice/mapper/model/PracticeProgressRow.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/practice/mapper/model/PracticeSessionRow.java`
- Create: `backend/mentor-api/src/main/resources/mapper/practice/PracticeSessionMapper.xml`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/practice/repository/MyBatisPracticeSessionRepository.java`
- Test: `backend/mentor-api/src/test/java/org/congcong/algomentor/api/practice/repository/MyBatisPracticeSessionRepositoryTest.java`

- [ ] **Step 1: Add Flyway migration**

Create `V12__practice_session_schema.sql`:

```sql
CREATE TABLE IF NOT EXISTS learning_plan_problem_progress (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  plan_id BIGINT NOT NULL,
  phase_index INT NOT NULL,
  problem_slug VARCHAR(255) NOT NULL,
  status VARCHAR(32) NOT NULL,
  started_at TIMESTAMPTZ NULL,
  completed_at TIMESTAMPTZ NULL,
  skipped_at TIMESTAMPTZ NULL,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uk_learning_plan_problem_progress UNIQUE (user_id, plan_id, phase_index, problem_slug),
  CONSTRAINT ck_learning_plan_problem_progress_status CHECK (status IN ('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED', 'SKIPPED'))
);

CREATE TABLE IF NOT EXISTS practice_session (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  plan_id BIGINT NOT NULL,
  phase_index INT NOT NULL,
  problem_slug VARCHAR(255) NOT NULL,
  status VARCHAR(32) NOT NULL,
  agent_task_id BIGINT NULL REFERENCES agent_task(id),
  problem_statement_message_id BIGINT NULL REFERENCES agent_message(id),
  last_message_at TIMESTAMPTZ NULL,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uk_practice_session_problem UNIQUE (user_id, plan_id, phase_index, problem_slug),
  CONSTRAINT uk_practice_session_agent_task UNIQUE (agent_task_id),
  CONSTRAINT ck_practice_session_status CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

CREATE INDEX IF NOT EXISTS idx_practice_session_user ON practice_session(user_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_learning_plan_problem_progress_plan ON learning_plan_problem_progress(user_id, plan_id, phase_index);
```

Use nullable `agent_task_id` during upsert so session row can be locked before task creation; enforce one-to-one with unique index when populated.

- [ ] **Step 2: Add mapper rows**

Create `PracticeProgressRow.java`:

```java
package org.congcong.algomentor.api.practice.mapper.model;

import java.time.Instant;

public record PracticeProgressRow(
    long id,
    long userId,
    long planId,
    int phaseIndex,
    String problemSlug,
    String status,
    Instant createdAt,
    Instant updatedAt
) {
}
```

Create `PracticeSessionRow.java`:

```java
package org.congcong.algomentor.api.practice.mapper.model;

import java.time.Instant;

public record PracticeSessionRow(
    long id,
    long userId,
    long planId,
    int phaseIndex,
    String problemSlug,
    String status,
    Long agentTaskId,
    Long problemStatementMessageId,
    String progressStatus,
    Instant lastMessageAt,
    Instant createdAt,
    Instant updatedAt
) {
}
```

- [ ] **Step 3: Add mapper interface**

Create `PracticeSessionMapper.java`:

```java
package org.congcong.algomentor.api.practice.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.congcong.algomentor.api.practice.mapper.model.PracticeProgressRow;
import org.congcong.algomentor.api.practice.mapper.model.PracticeSessionRow;

@Mapper
public interface PracticeSessionMapper {

  PracticeProgressRow upsertProgress(
      @Param("userId") long userId,
      @Param("planId") long planId,
      @Param("phaseIndex") int phaseIndex,
      @Param("problemSlug") String problemSlug
  );

  PracticeSessionRow upsertSession(
      @Param("userId") long userId,
      @Param("planId") long planId,
      @Param("phaseIndex") int phaseIndex,
      @Param("problemSlug") String problemSlug
  );

  PracticeSessionRow findSessionByIdForUser(
      @Param("sessionId") long sessionId,
      @Param("userId") long userId
  );

  PracticeSessionRow attachAgentTask(
      @Param("sessionId") long sessionId,
      @Param("agentTaskId") long agentTaskId
  );

  PracticeSessionRow attachProblemStatementMessage(
      @Param("sessionId") long sessionId,
      @Param("messageId") long messageId
  );

  PracticeProgressRow updateProgressStatus(
      @Param("sessionId") long sessionId,
      @Param("userId") long userId,
      @Param("status") String status
  );

  int touchLastMessageAt(@Param("sessionId") long sessionId);
}
```

- [ ] **Step 4: Add XML mapper**

Create `PracticeSessionMapper.xml` with result maps and SQL. Use `FOR UPDATE` on the session upsert result:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper
    PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
    "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="org.congcong.algomentor.api.practice.mapper.PracticeSessionMapper">

  <resultMap id="PracticeProgressMap" type="org.congcong.algomentor.api.practice.mapper.model.PracticeProgressRow">
    <constructor>
      <arg column="id" javaType="_long"/>
      <arg column="user_id" javaType="_long"/>
      <arg column="plan_id" javaType="_long"/>
      <arg column="phase_index" javaType="_int"/>
      <arg column="problem_slug" javaType="string"/>
      <arg column="status" javaType="string"/>
      <arg column="created_at" javaType="java.time.Instant"/>
      <arg column="updated_at" javaType="java.time.Instant"/>
    </constructor>
  </resultMap>

  <resultMap id="PracticeSessionMap" type="org.congcong.algomentor.api.practice.mapper.model.PracticeSessionRow">
    <constructor>
      <arg column="id" javaType="_long"/>
      <arg column="user_id" javaType="_long"/>
      <arg column="plan_id" javaType="_long"/>
      <arg column="phase_index" javaType="_int"/>
      <arg column="problem_slug" javaType="string"/>
      <arg column="status" javaType="string"/>
      <arg column="agent_task_id" javaType="java.lang.Long"/>
      <arg column="problem_statement_message_id" javaType="java.lang.Long"/>
      <arg column="progress_status" javaType="string"/>
      <arg column="last_message_at" javaType="java.time.Instant"/>
      <arg column="created_at" javaType="java.time.Instant"/>
      <arg column="updated_at" javaType="java.time.Instant"/>
    </constructor>
  </resultMap>

  <sql id="sessionSelect">
    SELECT
      s.id,
      s.user_id,
      s.plan_id,
      s.phase_index,
      s.problem_slug,
      s.status,
      s.agent_task_id,
      s.problem_statement_message_id,
      p.status AS progress_status,
      s.last_message_at,
      s.created_at,
      s.updated_at
    FROM practice_session s
    JOIN learning_plan_problem_progress p
      ON p.user_id = s.user_id
     AND p.plan_id = s.plan_id
     AND p.phase_index = s.phase_index
     AND p.problem_slug = s.problem_slug
  </sql>

  <select id="upsertProgress" resultMap="PracticeProgressMap">
    INSERT INTO learning_plan_problem_progress (
      user_id, plan_id, phase_index, problem_slug, status, started_at, created_at, updated_at
    )
    VALUES (#{userId}, #{planId}, #{phaseIndex}, #{problemSlug}, 'IN_PROGRESS', NOW(), NOW(), NOW())
    ON CONFLICT (user_id, plan_id, phase_index, problem_slug)
    DO UPDATE SET
      status = CASE
        WHEN learning_plan_problem_progress.status = 'COMPLETED' THEN 'COMPLETED'
        ELSE 'IN_PROGRESS'
      END,
      started_at = COALESCE(learning_plan_problem_progress.started_at, NOW()),
      updated_at = NOW()
    RETURNING id, user_id, plan_id, phase_index, problem_slug, status, created_at, updated_at
  </select>

  <select id="upsertSession" resultMap="PracticeSessionMap">
    WITH upserted AS (
      INSERT INTO practice_session (
        user_id, plan_id, phase_index, problem_slug, status, created_at, updated_at
      )
      VALUES (#{userId}, #{planId}, #{phaseIndex}, #{problemSlug}, 'ACTIVE', NOW(), NOW())
      ON CONFLICT (user_id, plan_id, phase_index, problem_slug)
      DO UPDATE SET updated_at = practice_session.updated_at
      RETURNING id
    )
    <include refid="sessionSelect"/>
    WHERE s.id = (SELECT id FROM upserted)
    FOR UPDATE
  </select>

  <select id="findSessionByIdForUser" resultMap="PracticeSessionMap">
    <include refid="sessionSelect"/>
    WHERE s.id = #{sessionId}
      AND s.user_id = #{userId}
  </select>

  <select id="attachAgentTask" resultMap="PracticeSessionMap">
    WITH updated AS (
      UPDATE practice_session
      SET agent_task_id = #{agentTaskId},
          updated_at = NOW()
      WHERE id = #{sessionId}
      RETURNING id
    )
    <include refid="sessionSelect"/>
    WHERE s.id = (SELECT id FROM updated)
  </select>

  <select id="attachProblemStatementMessage" resultMap="PracticeSessionMap">
    WITH updated AS (
      UPDATE practice_session
      SET problem_statement_message_id = #{messageId},
          updated_at = NOW()
      WHERE id = #{sessionId}
      RETURNING id
    )
    <include refid="sessionSelect"/>
    WHERE s.id = (SELECT id FROM updated)
  </select>

  <select id="updateProgressStatus" resultMap="PracticeProgressMap">
    UPDATE learning_plan_problem_progress p
    SET status = #{status},
        completed_at = CASE WHEN #{status} = 'COMPLETED' THEN COALESCE(completed_at, NOW()) ELSE completed_at END,
        updated_at = NOW()
    FROM practice_session s
    WHERE s.id = #{sessionId}
      AND s.user_id = #{userId}
      AND p.user_id = s.user_id
      AND p.plan_id = s.plan_id
      AND p.phase_index = s.phase_index
      AND p.problem_slug = s.problem_slug
    RETURNING p.id, p.user_id, p.plan_id, p.phase_index, p.problem_slug, p.status, p.created_at, p.updated_at
  </select>

  <update id="touchLastMessageAt">
    UPDATE practice_session
    SET last_message_at = NOW(),
        updated_at = NOW()
    WHERE id = #{sessionId}
  </update>
</mapper>
```

The `attachAgentTask` and `attachProblemStatementMessage` queries use a CTE so the mapper always returns the full joined session row, including current progress status.

- [ ] **Step 5: Implement repository adapter**

Create `MyBatisPracticeSessionRepository.java` mapping row strings to enums:

```java
package org.congcong.algomentor.api.practice.repository;

import java.util.Optional;
import org.congcong.algomentor.api.practice.mapper.PracticeSessionMapper;
import org.congcong.algomentor.api.practice.mapper.model.PracticeProgressRow;
import org.congcong.algomentor.api.practice.mapper.model.PracticeSessionRow;
import org.congcong.algomentor.mentor.application.practice.PracticeProgress;
import org.congcong.algomentor.mentor.application.practice.PracticeProgressStatus;
import org.congcong.algomentor.mentor.application.practice.PracticeSession;
import org.congcong.algomentor.mentor.application.practice.PracticeSessionRepository;
import org.congcong.algomentor.mentor.application.practice.PracticeSessionStatus;
import org.springframework.transaction.annotation.Transactional;

public class MyBatisPracticeSessionRepository implements PracticeSessionRepository {

  private final PracticeSessionMapper mapper;

  public MyBatisPracticeSessionRepository(PracticeSessionMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  @Transactional
  public PracticeProgress upsertAndAdvanceProgress(long userId, long planId, int phaseIndex, String problemSlug) {
    return toProgress(mapper.upsertProgress(userId, planId, phaseIndex, problemSlug));
  }

  @Override
  @Transactional
  public PracticeSession upsertAndLockSession(long userId, long planId, int phaseIndex, String problemSlug) {
    return toSession(mapper.upsertSession(userId, planId, phaseIndex, problemSlug));
  }

  @Override
  public Optional<PracticeSession> findSessionForUser(long sessionId, long userId) {
    return Optional.ofNullable(mapper.findSessionByIdForUser(sessionId, userId)).map(this::toSession);
  }

  @Override
  @Transactional
  public PracticeSession attachAgentTask(long sessionId, long agentTaskId) {
    return toSession(mapper.attachAgentTask(sessionId, agentTaskId));
  }

  @Override
  @Transactional
  public PracticeSession attachProblemStatementMessage(long sessionId, long messageId) {
    return toSession(mapper.attachProblemStatementMessage(sessionId, messageId));
  }

  @Override
  @Transactional
  public PracticeProgress updateProgressStatus(long sessionId, long userId, PracticeProgressStatus status) {
    return toProgress(mapper.updateProgressStatus(sessionId, userId, status.name()));
  }

  @Override
  public void touchLastMessageAt(long sessionId) {
    mapper.touchLastMessageAt(sessionId);
  }

  private PracticeProgress toProgress(PracticeProgressRow row) {
    return new PracticeProgress(
        row.id(), row.userId(), row.planId(), row.phaseIndex(), row.problemSlug(),
        PracticeProgressStatus.valueOf(row.status()), row.createdAt(), row.updatedAt());
  }

  private PracticeSession toSession(PracticeSessionRow row) {
    return new PracticeSession(
        row.id(), row.userId(), row.planId(), row.phaseIndex(), row.problemSlug(),
        PracticeSessionStatus.valueOf(row.status()), row.agentTaskId(), row.problemStatementMessageId(),
        PracticeProgressStatus.valueOf(row.progressStatus()), row.lastMessageAt(), row.createdAt(), row.updatedAt());
  }
}
```

- [ ] **Step 6: Run mapper-focused tests**

If the project has no Testcontainers setup for mapper integration, add a unit test for the adapter row mapping and run MyBatis XML parse through existing Spring slice only if already available. Otherwise run backend test compile:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-api -am -DskipTests compile
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/mentor-api/src/main/resources/db/migration/V12__practice_session_schema.sql \
  backend/mentor-api/src/main/java/org/congcong/algomentor/api/practice \
  backend/mentor-api/src/main/resources/mapper/practice
git commit -m "feat: add practice session persistence"
```

---

### Task 5: Practice Stream Service and Governance Source

**Files:**
- Modify: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/model/AiRunSource.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeMessageStreamService.java`
- Test: `backend/mentor-application/src/test/java/org/congcong/algomentor/mentor/application/practice/PracticeMessageStreamServiceTest.java`

- [ ] **Step 1: Add governance source**

Modify `AiRunSource.java`:

```java
public enum AiRunSource {
  LEARNING_PLAN_DRAFT,
  PROBLEM_DETAIL,
  LEARNING_CHAT,
  PRACTICE_CHAT,
  AI_DEBUG
}
```

- [ ] **Step 2: Write failing stream service test**

Create a test that builds `PracticeMessageStreamService` with in-memory session repository, plan repository, problem catalog, and a fake `AgentConversationRunCoordinator`. Verify:

```java
assertThat(command.practiceChat()).isEqualTo(new PracticeChatReference(12L, 1, "two-sum", "zh-CN"));
assertThat(command.taskId()).isEqualTo(100L);
assertThat(command.governanceMetadata()).containsEntry("practiceSessionId", 50L);
```

The test should also assert that `touchLastMessageAt(50L)` is invoked after the publisher emits `AgentRunEnd`.

- [ ] **Step 3: Implement `PracticeMessageStreamService`**

Create service with this public method:

```java
public Flow.Publisher<AgentStreamEvent> stream(
    long userId,
    long sessionId,
    String message,
    String idempotencyKey,
    String locale,
    Map<String, Object> governanceMetadata
)
```

Implementation outline:

```java
PracticeSession session = sessionRepository.findSessionForUser(sessionId, userId)
    .orElseThrow(() -> new LearningPlanException("PRACTICE_SESSION_NOT_FOUND", "题目训练会话不存在。"));
if (session.status() != PracticeSessionStatus.ACTIVE) {
  throw new LearningPlanException("PRACTICE_SESSION_ARCHIVED", "题目训练会话已归档。");
}
Map<String, Object> metadata = new HashMap<>(governanceMetadata == null ? Map.of() : governanceMetadata);
metadata.put(PracticeChatPromptConstants.METADATA_SCENARIO, PracticeChatPromptConstants.SCENARIO);
metadata.put(PracticeChatPromptConstants.METADATA_PRACTICE_SESSION_ID, session.id());
metadata.put(PracticeChatPromptConstants.METADATA_PLAN_ID, session.planId());
metadata.put(PracticeChatPromptConstants.METADATA_PHASE_INDEX, session.phaseIndex());
metadata.put(PracticeChatPromptConstants.METADATA_PROBLEM_SLUG, session.problemSlug());
metadata.put(PracticeChatPromptConstants.MESSAGE_TYPE_METADATA_KEY, PracticeChatPromptConstants.MESSAGE_TYPE_CHAT);

Flow.Publisher<AgentStreamEvent> delegate = runCoordinator.stream(new AgentConversationCommand(
    session.agentTaskId(),
    userId,
    message,
    idempotencyKey,
    Map.copyOf(metadata),
    new PracticeChatReference(session.planId(), session.phaseIndex(), session.problemSlug(), locale)));
return subscriber -> delegate.subscribe(new TouchLastMessageSubscriber(subscriber, session.id(), sessionRepository));
```

`TouchLastMessageSubscriber` forwards all events and calls `sessionRepository.touchLastMessageAt(sessionId)` when it sees `AgentStreamEvent.AgentRunEnd`.

- [ ] **Step 4: Run tests and commit**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl ai-governance,mentor-application -am -Dtest=PracticeMessageStreamServiceTest test
```

Expected: PASS.

Commit:

```bash
git add backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/model/AiRunSource.java \
  backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeMessageStreamService.java \
  backend/mentor-application/src/test/java/org/congcong/algomentor/mentor/application/practice/PracticeMessageStreamServiceTest.java
git commit -m "feat: add practice message stream service"
```

---

### Task 6: Practice Session API

**Files:**
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/config/ApiContractConstants.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/practice/model/PracticeSessionResponse.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/practice/model/PracticeSessionSummaryResponse.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/practice/model/PracticeProblemSummaryResponse.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/practice/model/PracticeMessageResponse.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/practice/model/PracticeProgressStatusRequest.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/practice/model/PracticeMessageRequest.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/practice/model/PracticeSessionResponseMapper.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/practice/PracticeSessionController.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/practice/PracticeSessionExceptionHandler.java`
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/mentor/api/autoconfigure/AgentConversationApiAutoConfiguration.java`
- Test: `backend/mentor-api/src/test/java/org/congcong/algomentor/api/controller/practice/PracticeSessionControllerTest.java`

- [ ] **Step 1: Add API constants**

Add to `ApiContractConstants`:

```java
  public static final String PRACTICE_SESSIONS_BASE_PATH = "/api/practice-sessions";

  public static final String LEARNING_PLAN_PROBLEM_PRACTICE_SESSION_PATH =
      "/{planId}/phases/{phaseIndex}/problems/{slug}/practice-session";

  public static final String PRACTICE_SESSION_MESSAGES_STREAM_PATH = "/{sessionId}/messages/stream";

  public static final String PRACTICE_SESSION_PROGRESS_STATUS_PATH = "/{sessionId}/progress-status";
```

- [ ] **Step 2: Create response/request records**

Create records:

```java
public record PracticeSessionResponse(
    PracticeSessionSummaryResponse session,
    PracticeProblemSummaryResponse problem,
    List<PracticeMessageResponse> messages
) {
}
```

```java
public record PracticeSessionSummaryResponse(
    long id,
    long planId,
    int phaseIndex,
    String problemSlug,
    String progressStatus,
    long agentTaskId,
    Instant createdAt,
    Instant updatedAt
) {
}
```

```java
public record PracticeProblemSummaryResponse(
    String slug,
    Integer frontendId,
    String title,
    String titleCn,
    String difficulty,
    List<String> tags,
    String leetcodeUrl
) {
}
```

```java
public record PracticeMessageResponse(
    long id,
    String role,
    String messageType,
    String contentMarkdown,
    Instant createdAt
) {
}
```

```java
public record PracticeProgressStatusRequest(String status) {
}
```

```java
public record PracticeMessageRequest(String message) {
}
```

- [ ] **Step 3: Add mapper**

`PracticeSessionResponseMapper.toResponse(PracticeSessionResult result)` should map:

```java
return new PracticeSessionResponse(
    new PracticeSessionSummaryResponse(
        result.session().id(),
        result.session().planId(),
        result.session().phaseIndex(),
        result.session().problemSlug(),
        result.session().progressStatus().name(),
        result.session().agentTaskId(),
        result.session().createdAt(),
        result.session().updatedAt()),
    new PracticeProblemSummaryResponse(
        result.problem().slug(),
        result.problem().frontendId(),
        result.problem().title(),
        null,
        result.problem().difficulty(),
        result.problem().tags(),
        result.problem().leetcodeUrl()),
    result.messages().stream()
        .map(message -> new PracticeMessageResponse(
            message.id(),
            message.role(),
            message.messageType(),
            message.contentMarkdown(),
            message.createdAt()))
        .toList());
```

- [ ] **Step 4: Write controller tests**

Create `PracticeSessionControllerTest` with tests:

```java
mockMvc.perform(post("/api/learning-plans/12/phases/1/problems/two-sum/practice-session?locale=zh-CN"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.data.session.id").value(50))
    .andExpect(jsonPath("$.data.messages[0].messageType").value("PROBLEM_STATEMENT"));
```

```java
mockMvc.perform(get("/api/practice-sessions/50"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.data.session.progressStatus").value("IN_PROGRESS"));
```

```java
mockMvc.perform(patch("/api/practice-sessions/50/progress-status")
    .contentType(MediaType.APPLICATION_JSON)
    .content("{\"status\":\"COMPLETED\"}"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.data.session.progressStatus").value("COMPLETED"));
```

For streaming:

```java
mockMvc.perform(post("/api/practice-sessions/50/messages/stream")
    .header("Idempotency-Key", "idem-practice")
    .contentType(MediaType.APPLICATION_JSON)
    .accept(MediaType.TEXT_EVENT_STREAM)
    .content("{\"message\":\"我想先用哈希表\"}"))
    .andReturn();
```

Verify the captured `AiRunContext`:

```java
assertThat(captor.getValue().source()).isEqualTo(AiRunSource.PRACTICE_CHAT);
assertThat(captor.getValue().purpose()).isEqualTo(AiPurpose.LEARNING_CHAT);
assertThat(captor.getValue().metadata()).containsEntry("practiceSessionId", 50L);
```

- [ ] **Step 5: Implement controller**

`PracticeSessionController` has two base mappings: learning-plan nested create endpoint and `/api/practice-sessions`. Inject `PracticeSessionService`, `PracticeMessageStreamService`, `CurrentUserIdProvider`, `AiActorResolver`, `AiRunAdmissionService`, `LlmStreamSseMapper`.

Streaming method must:

```java
String effectiveKey = idempotencyKey == null || idempotencyKey.isBlank()
    ? UUID.randomUUID().toString()
    : idempotencyKey;
AiRunAdmission admission = admissionService.admit(new AiRunContext(
    UUID.randomUUID().toString(),
    actorResolver.currentActor(),
    AiPurpose.LEARNING_CHAT,
    AiRunSource.PRACTICE_CHAT,
    effectiveKey,
    request.message() == null ? 0 : request.message().getBytes(StandardCharsets.UTF_8).length,
    true,
    Map.of(PracticeChatPromptConstants.METADATA_PRACTICE_SESSION_ID, sessionId),
    Instant.now()));
Flow.Publisher<AgentStreamEvent> publisher = streamService.stream(
    userId, sessionId, request.message(), effectiveKey, "zh-CN", admission.metadata());
```

Use `SseLlmStreamSubscriber` exactly as `AgentConversationController` does.

- [ ] **Step 6: Wire beans**

In `AgentConversationApiAutoConfiguration`, add beans for:

- `PracticeSessionService`
- `PracticeMessageStreamService`
- `MyBatisPracticeSessionRepository`
- `PracticeSessionController`

Conditions must require the relevant repositories and coordinator. Do not remove existing generic conversation beans.

- [ ] **Step 7: Run API tests and commit**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-api -am -Dtest=PracticeSessionControllerTest test
```

Expected: PASS.

Commit:

```bash
git add backend/mentor-api/src/main/java/org/congcong/algomentor/api/config/ApiContractConstants.java \
  backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/practice \
  backend/mentor-api/src/main/java/org/congcong/algomentor/api/practice/model \
  backend/mentor-api/src/main/java/org/congcong/algomentor/mentor/api/autoconfigure/AgentConversationApiAutoConfiguration.java \
  backend/mentor-api/src/test/java/org/congcong/algomentor/api/controller/practice
git commit -m "feat: add practice session API"
```

---

### Task 7: Frontend Practice API Types and Service

**Files:**
- Modify: `frontend/src/types/api.ts`
- Modify: `frontend/src/services/api.ts`
- Test: `frontend/src/App.test.tsx` or `frontend/src/services/api.test.ts` if service tests exist

- [ ] **Step 1: Add TypeScript types**

Add to `frontend/src/types/api.ts`:

```ts
export type PracticeProgressStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED' | 'SKIPPED';
export type PracticeMessageRole = 'USER' | 'ASSISTANT';
export type PracticeMessageType = 'PROBLEM_STATEMENT' | 'CHAT';

export interface PracticeSessionSummary {
  id: number;
  planId: number;
  phaseIndex: number;
  problemSlug: string;
  progressStatus: PracticeProgressStatus;
  agentTaskId: number;
  createdAt: string;
  updatedAt: string;
}

export interface PracticeProblemSummary {
  slug: string;
  frontendId?: number;
  title: string;
  titleCn?: string | null;
  difficulty?: ProblemDifficulty | string;
  tags: string[];
  leetcodeUrl?: string;
}

export interface PracticeMessage {
  id: number;
  role: PracticeMessageRole;
  messageType: PracticeMessageType;
  contentMarkdown: string;
  createdAt: string;
}

export interface PracticeSessionResponse {
  session: PracticeSessionSummary;
  problem: PracticeProblemSummary;
  messages: PracticeMessage[];
}

export interface PracticeMessageRequest {
  message: string;
}
```

- [ ] **Step 2: Add service imports**

Add to `frontend/src/services/api.ts` imports:

```ts
  PracticeMessageRequest,
  PracticeProgressStatus,
  PracticeSessionResponse,
```

- [ ] **Step 3: Add API functions**

Add:

```ts
export async function createOrReusePracticeSession(
  planId: number,
  phaseIndex: number,
  problemSlug: string,
  locale?: ProblemListQuery['locale'],
  signal?: AbortSignal,
): Promise<ApiResponse<PracticeSessionResponse>> {
  const response = await apiFetch(
    `/api/learning-plans/${planId}/phases/${phaseIndex}/problems/${encodeURIComponent(problemSlug)}/practice-session${toQueryString({ locale })}`,
    {
      method: 'POST',
      headers: jsonHeaders,
      signal,
    },
  );

  if (!response.ok) {
    throw await toApiRequestError(response, 'Practice session request failed');
  }

  return response.json();
}

export async function getPracticeSession(
  sessionId: number,
  signal?: AbortSignal,
): Promise<ApiResponse<PracticeSessionResponse>> {
  const response = await apiFetch(`/api/practice-sessions/${sessionId}`, {
    headers: jsonHeaders,
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Practice session detail request failed');
  }

  return response.json();
}

export async function updatePracticeProgressStatus(
  sessionId: number,
  status: Extract<PracticeProgressStatus, 'COMPLETED'>,
): Promise<ApiResponse<PracticeSessionResponse>> {
  const response = await apiFetch(`/api/practice-sessions/${sessionId}/progress-status`, {
    method: 'PATCH',
    headers: {
      ...jsonHeaders,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ status }),
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Practice progress update failed');
  }

  return response.json();
}
```

Add stream:

```ts
export interface StreamPracticeMessageOptions {
  idempotencyKey: string;
  signal?: AbortSignal;
  onOpen?: () => void;
  onEvent: (event: SseStreamEvent) => void;
}

export async function streamPracticeMessage(
  sessionId: number,
  request: PracticeMessageRequest,
  options: StreamPracticeMessageOptions,
): Promise<void> {
  const response = await apiFetch(`/api/practice-sessions/${sessionId}/messages/stream`, {
    method: 'POST',
    headers: {
      Accept: 'text/event-stream, application/json',
      'Content-Type': 'application/json',
      'Idempotency-Key': options.idempotencyKey,
    },
    body: JSON.stringify(request),
    signal: options.signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Practice message stream failed');
  }
  if (!response.body) {
    throw new Error('Practice message stream response does not include a readable body');
  }

  options.onOpen?.();
  await readEventStream(response.body, options.onEvent);
}
```

- [ ] **Step 4: Run frontend typecheck/tests**

Run:

```bash
npm --cache ./.npm --prefix frontend test -- --run
```

Expected: PASS or known existing tests pass. If dependencies are not installed, run `make frontend-install` first.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/types/api.ts frontend/src/services/api.ts
git commit -m "feat: add practice session frontend API"
```

---

### Task 8: Frontend Practice Chat Workbench

**Files:**
- Modify: `frontend/src/learning-plans/PracticeChatWorkbench.tsx`
- Modify: `frontend/src/i18n/locales.ts`
- Test: `frontend/src/App.test.tsx` or `frontend/src/learning-plans/PracticeChatWorkbench.test.tsx`

- [ ] **Step 1: Write failing UI test**

Add a test that mocks:

- `POST /api/learning-plans/900/phases/1/problems/two-sum/practice-session?locale=zh-CN`
- `POST /api/practice-sessions/50/messages/stream`
- `PATCH /api/practice-sessions/50/progress-status`

Assertions:

```ts
expect(await screen.findByText(/Two Sum/)).toBeInTheDocument();
expect(screen.getByText(/# Two Sum/i)).toBeInTheDocument();
fireEvent.change(screen.getByRole('textbox', { name: /message|消息|输入/i }), {
  target: { value: '我想先用哈希表' },
});
fireEvent.click(screen.getByRole('button', { name: /send|发送/i }));
expect(await screen.findByText('我想先用哈希表')).toBeInTheDocument();
expect(await screen.findByText(/正在组织思路|organizing/i)).toBeInTheDocument();
```

- [ ] **Step 2: Run frontend test to verify it fails**

Run:

```bash
npm --cache ./.npm --prefix frontend test -- --run PracticeChatWorkbench
```

Expected: FAIL because workbench still uses `getProblemDetail` and disabled composer.

- [ ] **Step 3: Replace problem-detail loading with practice session loading**

In `PracticeChatWorkbench.tsx`, import:

```ts
import {
  createOrReusePracticeSession,
  streamPracticeMessage,
  updatePracticeProgressStatus,
} from '../services/api';
import type { PracticeMessage, PracticeSessionResponse } from '../types/api';
```

State:

```ts
const [sessionResponse, setSessionResponse] = useState<PracticeSessionResponse>();
const [messages, setMessages] = useState<PracticeMessage[]>([]);
const [composerValue, setComposerValue] = useState('');
const [status, setStatus] = useState<'loading' | 'idle' | 'streaming' | 'error' | 'blocked'>('loading');
const [error, setError] = useState('');
```

Load session:

```ts
useEffect(() => {
  const controller = new AbortController();
  setStatus('loading');
  setError('');

  createOrReusePracticeSession(plan.id, phaseIndex, problemSlug, locale, controller.signal)
    .then((response) => {
      if (!response.success || !response.data) {
        throw new Error(response.error?.message ?? resources.learningPlans.detailLoadProblemFailed);
      }
      setSessionResponse(response.data);
      setMessages(response.data.messages);
      setStatus('idle');
    })
    .catch((loadError) => {
      if (!controller.signal.aborted) {
        setError(loadError instanceof Error ? loadError.message : resources.learningPlans.detailLoadProblemFailed);
        setStatus('error');
      }
    });

  return () => controller.abort();
}, [locale, phaseIndex, plan.id, problemSlug, resources.learningPlans.detailLoadProblemFailed]);
```

- [ ] **Step 4: Implement sending**

Add `handleSubmit`:

```ts
async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
  event.preventDefault();
  const text = composerValue.trim();
  const sessionId = sessionResponse?.session.id;
  if (!text || !sessionId || status === 'streaming') {
    return;
  }

  const now = new Date().toISOString();
  const pendingAssistantId = -Date.now();
  setMessages((current) => [
    ...current,
    {
      id: pendingAssistantId - 1,
      role: 'USER',
      messageType: 'CHAT',
      contentMarkdown: text,
      createdAt: now,
    },
    {
      id: pendingAssistantId,
      role: 'ASSISTANT',
      messageType: 'CHAT',
      contentMarkdown: resources.learningPlans.organizingThoughts,
      createdAt: now,
    },
  ]);
  setComposerValue('');
  setStatus('streaming');

  const controller = new AbortController();
  try {
    await streamPracticeMessage(
      sessionId,
      { message: text },
      {
        idempotencyKey: crypto.randomUUID(),
        signal: controller.signal,
        onEvent: ({ eventName, data }) => {
          if (eventName === 'content_delta' && typeof data === 'object' && data && 'content' in data) {
            const delta = String((data as { content?: string }).content ?? '');
            setMessages((current) => current.map((message) => (
              message.id === pendingAssistantId
                ? { ...message, contentMarkdown: message.contentMarkdown === resources.learningPlans.organizingThoughts ? delta : message.contentMarkdown + delta }
                : message
            )));
          }
          if (eventName === 'agent_run_end' || eventName === 'message_end') {
            setStatus('idle');
          }
        },
      },
    );
    setStatus('idle');
  } catch (streamError) {
    setStatus(streamError instanceof ApiRequestError && streamError.code === AGENT_RUN_IN_PROGRESS_CODE ? 'blocked' : 'error');
    setMessages((current) => current.map((message) => (
      message.id === pendingAssistantId
        ? { ...message, contentMarkdown: resources.learningPlans.replyFailed }
        : message
    )));
  }
}
```

Add missing imports for `ApiRequestError` and `AGENT_RUN_IN_PROGRESS_CODE`.

- [ ] **Step 5: Implement completion button**

Add handler:

```ts
async function markCompleted() {
  const sessionId = sessionResponse?.session.id;
  if (!sessionId) {
    return;
  }
  const response = await updatePracticeProgressStatus(sessionId, 'COMPLETED');
  if (response.success && response.data) {
    setSessionResponse(response.data);
    setMessages(response.data.messages);
  }
}
```

Button disabled condition:

```tsx
disabled={!sessionResponse || sessionResponse.session.progressStatus === 'COMPLETED'}
```

- [ ] **Step 6: Add locale strings**

Add to both locales under `learningPlans`:

```ts
organizingThoughts: '正在组织思路...',
replyFailed: '回复失败，请重试。',
markCompleted: '题目已完成',
completed: '已完成',
```

For English:

```ts
organizingThoughts: 'Organizing the answer...',
replyFailed: 'Reply failed. Please retry.',
markCompleted: 'Mark complete',
completed: 'Completed',
```

- [ ] **Step 7: Run frontend tests and commit**

Run:

```bash
npm --cache ./.npm --prefix frontend test -- --run
```

Expected: PASS.

Commit:

```bash
git add frontend/src/learning-plans/PracticeChatWorkbench.tsx frontend/src/i18n/locales.ts frontend/src/App.test.tsx frontend/src/learning-plans/PracticeChatWorkbench.test.tsx
git commit -m "feat: connect practice chat workbench"
```

---

### Task 9: Full Verification and Cleanup

**Files:**
- Review: `docs/practice-chat-agent-design.md`
- Review: `docs/code-index.md`
- Review all files changed in prior tasks

- [ ] **Step 1: Run backend tests**

Run:

```bash
make backend-test
```

Expected: PASS.

- [ ] **Step 2: Run frontend tests**

Run:

```bash
make frontend-test
```

Expected: PASS.

- [ ] **Step 3: Run package build if tests pass**

Run:

```bash
make build
```

Expected: PASS.

- [ ] **Step 4: Inspect git diff**

Run:

```bash
git status --short
git diff --stat HEAD
```

Expected: only intentional practice chat implementation files are changed.

- [ ] **Step 5: Update docs/code-index.md**

Update `docs/code-index.md` with entries for the new permanent modules:

- practice session controller
- practice session repository
- practice message stream service

Do not update docs for generated `target`, `dist`, or static build assets.

- [ ] **Step 6: Commit final documentation update**

```bash
git add docs/code-index.md
git commit -m "docs: update practice chat code index"
```

Run this commit only when `docs/code-index.md` changed.

---

## Self-Review

- Spec coverage: The plan covers practice tables, task/message metadata, seed message creation, create/get/update APIs, streaming API, Prompt Assembly integration through `AgentConversationService`, governance source, frontend API functions, chat UI state, SSE delta rendering, and verification.
- Placeholder scan: The plan has no unresolved placeholders. SQL update queries use the CTE shape directly so mapper return rows are deterministic.
- Type consistency: Java package names match existing modules. Metadata keys use `PracticeChatPromptConstants`. Frontend types match the API response structure in `docs/practice-chat-agent-design.md`.
