# Practice Code Review Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:using-git-worktrees before code changes, then use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. When using subagents, use the high model level requested by the user.

**Goal:** Build automatic practice code Review for practice chat: detect complete LeetCode submissions, save structured multi-version Review records, expose Review history, and enforce completion by the latest valid Review.

**Architecture:** Extend the existing practice chat flow with a lightweight `PracticeTurnOrchestrator` and server-side `PracticeTurnCapability`. Chat SSE remains driven by `AgentConversationRunCoordinator`; Review is generated after `AgentRunEnd` via non-streaming JSON Schema LLM output and persisted as an independent `practice_code_review` fact table. `PracticeSessionService` aggregates latest Review and completion gate into session responses, while the frontend refreshes messages/session/reviews after each run.

**Tech Stack:** Java 17, Spring MVC/SSE, Maven, MyBatis, PostgreSQL/Flyway, Jackson JSONB, Micrometer, `llm-core` JSON Schema structured output, Agent runtime, React 19, TypeScript, Vite, Vitest/React Testing Library.

---

## Reference Specs

- `docs/superpowers/specs/2026-06-25-practice-code-review-design.md`
- `docs/practice-code-review-product-design.md`
- `docs/practice-code-review-technical-design.md`
- `docs/code-index.md`

## Execution Setup

Implementation must start in an isolated worktree:

```bash
GIT_DIR=$(cd "$(git rev-parse --git-dir)" && pwd -P)
GIT_COMMON=$(cd "$(git rev-parse --git-common-dir)" && pwd -P)
git rev-parse --show-superproject-working-tree 2>/dev/null
git check-ignore -q .worktrees
git worktree add .worktrees/practice-code-review -b feat/practice-code-review
cd .worktrees/practice-code-review
```

Baseline verification in the worktree:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository test
npm --cache ./.npm --prefix frontend test -- --run
```

If baseline tests fail, record the failures before implementing. Do not start Vite or `make frontend-dev` unless the user explicitly asks.

## File Structure

### Agent runtime lookup

- Create: `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/runtime/model/AgentTurnMessages.java`  
  Holds run id, turn id, required user message, and nullable assistant message.
- Create: `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/runtime/repository/AgentTurnMessageLookupRepository.java`  
  Port for looking up the messages attached to an accepted run.
- Modify: `backend/agent-persistence-postgres/src/main/java/org/congcong/algomentor/agent/persistence/postgres/mapper/AgentConversationMapper.java`  
  Add `findTurnMessagesByRunId`.
- Modify: `backend/agent-persistence-postgres/src/main/resources/mapper/agent/AgentConversationMapper.xml`  
  Add result maps and SQL joining `agent_run -> agent_turn -> agent_message`.
- Create: `backend/agent-persistence-postgres/src/main/java/org/congcong/algomentor/agent/persistence/postgres/repository/PostgresAgentTurnMessageLookupRepository.java`
- Modify: `backend/agent-persistence-postgres/src/main/java/org/congcong/algomentor/agent/persistence/postgres/config/AgentPostgresPersistenceConfiguration.java`  
  Register lookup repository bean.

### Practice application domain

- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeCodeReviewConstants.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeCodeReview.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeCodeReviewDraft.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeCodeReviewSummary.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeCodeReviewScore.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeCodeReviewEvidence.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeCompletionGate.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeCompletionGateService.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeCodeReviewRepository.java`
- Modify: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeSessionResult.java`
- Modify: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeSessionService.java`

### Practice turn orchestration and Review

- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeTurnContext.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeTurnClassification.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeTurnClassifier.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeTurnCapability.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeTurnCapabilityRegistry.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeTurnCapabilityResult.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeTurnOrchestrator.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeReviewStatus.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeReviewResult.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/CodeReviewTurnCapability.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeCodeReviewJsonSchema.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeCodeReviewPromptBuilder.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeCodeReviewStructuredOutputMapper.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeCodeReviewService.java`
- Modify: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeMessageStreamService.java`
- Modify: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeChatPromptSectionProvider.java`

### Practice API and persistence

- Create: `backend/mentor-api/src/main/resources/db/migration/V13__practice_code_review_schema.sql`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/practice/mapper/PracticeCodeReviewMapper.java`
- Create: `backend/mentor-api/src/main/resources/mapper/practice/PracticeCodeReviewMapper.xml`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/practice/mapper/model/PracticeCodeReviewInsertRow.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/practice/mapper/model/PracticeCodeReviewRow.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/practice/mapper/model/PracticeCodeReviewSummaryRow.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/practice/repository/MyBatisPracticeCodeReviewRepository.java`
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/config/MentorApiMyBatisConfiguration.java`
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/config/ApiContractConstants.java`
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/mentor/api/autoconfigure/AgentConversationApiAutoConfiguration.java`
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/practice/PracticeSessionController.java`
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/practice/model/PracticeSessionResponse.java`
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/practice/model/PracticeSessionResponseMapper.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/practice/model/PracticeCompletionGateResponse.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/practice/model/PracticeCodeReviewSummaryResponse.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/practice/model/PracticeCodeReviewHistoryResponse.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/practice/model/PracticeCodeReviewDetailResponse.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/practice/model/PracticeCodeReviewScoreResponse.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/practice/model/PracticeCodeReviewEvidenceResponse.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/practice/model/PracticeCodeReviewResponseMapper.java`

### Frontend

- Modify: `frontend/src/types/api.ts`
- Modify: `frontend/src/services/api.ts`
- Modify: `frontend/src/i18n/locales.ts`
- Modify: `frontend/src/learning-plans/PracticeChatWorkbench.tsx`
- Create: `frontend/src/learning-plans/ReviewHistoryDrawer.tsx`
- Create: `frontend/src/learning-plans/ReviewScoreBadge.tsx`
- Create: `frontend/src/learning-plans/ReviewVersionList.tsx`
- Create: `frontend/src/learning-plans/ReviewDetailPanel.tsx`
- Create: `frontend/src/learning-plans/CompletionGateHint.tsx`
- Modify: `frontend/src/styles.css`
- Create: `frontend/src/learning-plans/PracticeChatWorkbench.test.tsx`
- Modify: `frontend/src/App.test.tsx`

---

### Task 1: Agent Turn Message Lookup Port

**Files:**
- Create: `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/runtime/model/AgentTurnMessages.java`
- Create: `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/runtime/repository/AgentTurnMessageLookupRepository.java`
- Test: `backend/agent-core/src/test/java/org/congcong/algomentor/agent/core/runtime/model/AgentTurnMessagesTest.java`

- [ ] **Step 1: Write failing model test**

Create `AgentTurnMessagesTest`:

```java
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
  void exposesEmptyAssistantWhenMissing() {
    AgentTurnMessages messages = new AgentTurnMessages(80L, 70L, message(10, AgentMessage.Role.USER), null);

    assertThat(messages.assistantMessage()).isEqualTo(Optional.empty());
  }

  private AgentMessage message(long id, AgentMessage.Role role) {
    return new AgentMessage(id, 5L, id, role, "content " + id, Instant.parse("2026-01-01T00:00:00Z"));
  }
}
```

- [ ] **Step 2: Run RED**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl agent-core -Dtest=AgentTurnMessagesTest test
```

Expected: compilation fails because `AgentTurnMessages` does not exist.

- [ ] **Step 3: Implement model and port**

Create `AgentTurnMessages.java`:

```java
package org.congcong.algomentor.agent.core.runtime.model;

import java.util.Optional;

public record AgentTurnMessages(
    long runId,
    long turnId,
    AgentMessage userMessage,
    AgentMessage assistantMessage
) {

  public AgentTurnMessages {
    if (runId < 1 || turnId < 1) {
      throw new IllegalArgumentException("Agent turn message ids must be positive");
    }
    if (userMessage == null || userMessage.role() != AgentMessage.Role.USER) {
      throw new IllegalArgumentException("Agent turn user message is required");
    }
    if (assistantMessage != null && assistantMessage.role() != AgentMessage.Role.ASSISTANT) {
      throw new IllegalArgumentException("Agent turn assistant message must have assistant role");
    }
  }

  public Optional<AgentMessage> assistantMessage() {
    return Optional.ofNullable(assistantMessage);
  }
}
```

Create `AgentTurnMessageLookupRepository.java`:

```java
package org.congcong.algomentor.agent.core.runtime.repository;

import java.util.Optional;
import org.congcong.algomentor.agent.core.runtime.model.AgentTurnMessages;

public interface AgentTurnMessageLookupRepository {

  Optional<AgentTurnMessages> findByRunId(long runId);
}
```

- [ ] **Step 4: Run GREEN**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl agent-core -Dtest=AgentTurnMessagesTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/runtime/model/AgentTurnMessages.java \
  backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/runtime/repository/AgentTurnMessageLookupRepository.java \
  backend/agent-core/src/test/java/org/congcong/algomentor/agent/core/runtime/model/AgentTurnMessagesTest.java
git commit -m "feat: add agent turn message lookup port"
```

### Task 2: Postgres Run Message Lookup

**Files:**
- Modify: `backend/agent-persistence-postgres/src/main/java/org/congcong/algomentor/agent/persistence/postgres/mapper/AgentConversationMapper.java`
- Modify: `backend/agent-persistence-postgres/src/main/resources/mapper/agent/AgentConversationMapper.xml`
- Create: `backend/agent-persistence-postgres/src/main/java/org/congcong/algomentor/agent/persistence/postgres/mapper/model/AgentTurnMessagesRow.java`
- Create: `backend/agent-persistence-postgres/src/main/java/org/congcong/algomentor/agent/persistence/postgres/repository/PostgresAgentTurnMessageLookupRepository.java`
- Modify: `backend/agent-persistence-postgres/src/main/java/org/congcong/algomentor/agent/persistence/postgres/config/AgentPostgresPersistenceConfiguration.java`
- Test: `backend/agent-persistence-postgres/src/test/java/org/congcong/algomentor/agent/persistence/postgres/repository/PostgresAgentTurnMessageLookupRepositoryTest.java`
- Test: `backend/agent-persistence-postgres/src/test/java/org/congcong/algomentor/agent/persistence/postgres/mapper/AgentMapperXmlTest.java`

- [ ] **Step 1: Write failing repository test**

Create `PostgresAgentTurnMessageLookupRepositoryTest`:

```java
package org.congcong.algomentor.agent.persistence.postgres.repository;

import static org.assertj.core.api.Assertions.assertThat;
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
```

- [ ] **Step 2: Add XML parsing assertion first**

Modify `AgentMapperXmlTest` to assert the statement exists:

```java
assertThat(configuration.hasStatement(namespace + "findTurnMessagesByRunId")).isTrue();
```

- [ ] **Step 3: Run RED**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl agent-persistence-postgres -am -Dtest=PostgresAgentTurnMessageLookupRepositoryTest,AgentMapperXmlTest test
```

Expected: compilation/XML assertion fails.

- [ ] **Step 4: Implement row, mapper, SQL, repository, bean**

Create `AgentTurnMessagesRow.java`:

```java
package org.congcong.algomentor.agent.persistence.postgres.mapper.model;

import java.time.Instant;
import java.util.Map;

public record AgentTurnMessagesRow(
    long runId,
    long turnId,
    long taskId,
    long userMessageId,
    long userSequenceNo,
    String userContent,
    Instant userCreatedAt,
    Map<String, Object> userMetadata,
    Long assistantMessageId,
    Long assistantSequenceNo,
    String assistantContent,
    Instant assistantCreatedAt,
    Map<String, Object> assistantMetadata
) {
}
```

Add to `AgentConversationMapper.java`:

```java
AgentTurnMessagesRow findTurnMessagesByRunId(@Param("runId") long runId);
```

Add to `AgentConversationMapper.xml`:

```xml
<resultMap id="AgentTurnMessagesRowMap"
    type="org.congcong.algomentor.agent.persistence.postgres.mapper.model.AgentTurnMessagesRow">
  <constructor>
    <arg column="run_id" javaType="_long"/>
    <arg column="turn_id" javaType="_long"/>
    <arg column="task_id" javaType="_long"/>
    <arg column="user_message_id" javaType="_long"/>
    <arg column="user_sequence_no" javaType="_long"/>
    <arg column="user_content" javaType="string"/>
    <arg column="user_created_at" javaType="java.time.Instant"/>
    <arg column="user_metadata" javaType="java.util.Map"
         typeHandler="org.congcong.algomentor.agent.persistence.postgres.json.JsonbMapTypeHandler"/>
    <arg column="assistant_message_id" javaType="java.lang.Long"/>
    <arg column="assistant_sequence_no" javaType="java.lang.Long"/>
    <arg column="assistant_content" javaType="string"/>
    <arg column="assistant_created_at" javaType="java.time.Instant"/>
    <arg column="assistant_metadata" javaType="java.util.Map"
         typeHandler="org.congcong.algomentor.agent.persistence.postgres.json.JsonbMapTypeHandler"/>
  </constructor>
</resultMap>

<select id="findTurnMessagesByRunId" resultMap="AgentTurnMessagesRowMap">
  SELECT
    r.id AS run_id,
    t.id AS turn_id,
    r.task_id,
    u.id AS user_message_id,
    u.sequence_no AS user_sequence_no,
    u.content AS user_content,
    u.created_at AS user_created_at,
    u.metadata AS user_metadata,
    a.id AS assistant_message_id,
    a.sequence_no AS assistant_sequence_no,
    a.content AS assistant_content,
    a.created_at AS assistant_created_at,
    a.metadata AS assistant_metadata
  FROM agent_run r
  JOIN agent_turn t ON t.id = r.turn_id
  JOIN agent_message u ON u.id = t.user_message_id AND u.status = 'active'
  LEFT JOIN agent_message a ON a.id = t.assistant_message_id AND a.status = 'active'
  WHERE r.id = #{runId}
</select>
```

Create `PostgresAgentTurnMessageLookupRepository.java`:

```java
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
```

Register in `AgentPostgresPersistenceConfiguration`:

```java
@Bean
@ConditionalOnBean(AgentConversationMapper.class)
@ConditionalOnMissingBean
public AgentTurnMessageLookupRepository agentTurnMessageLookupRepository(AgentConversationMapper conversationMapper) {
  return new PostgresAgentTurnMessageLookupRepository(conversationMapper);
}
```

- [ ] **Step 5: Run GREEN**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl agent-persistence-postgres -am -Dtest=PostgresAgentTurnMessageLookupRepositoryTest,AgentMapperXmlTest test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/agent-core backend/agent-persistence-postgres
git commit -m "feat: add postgres agent turn message lookup"
```

### Task 3: Practice Review Domain and Completion Gate

**Files:**
- Create domain files listed under “Practice application domain”
- Modify: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeSessionResult.java`
- Modify: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeSessionService.java`
- Test: `backend/mentor-application/src/test/java/org/congcong/algomentor/mentor/application/practice/PracticeCompletionGateServiceTest.java`
- Test: `backend/mentor-application/src/test/java/org/congcong/algomentor/mentor/application/practice/PracticeSessionServiceTest.java`

- [ ] **Step 1: Write failing completion gate tests**

Create `PracticeCompletionGateServiceTest`:

```java
package org.congcong.algomentor.mentor.application.practice;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PracticeCompletionGateServiceTest {

  @Test
  void blocksWhenNoReviewExists() {
    PracticeCompletionGate gate = service(Optional.empty())
        .evaluate(7L, session(PracticeProgressStatus.IN_PROGRESS));

    assertThat(gate.canComplete()).isFalse();
    assertThat(gate.reasonCode()).isEqualTo(PracticeCompletionGate.ReasonCode.NO_REVIEW);
    assertThat(gate.passScore()).isEqualByComparingTo("6.0");
  }

  @Test
  void blocksWhenLatestReviewFailed() {
    PracticeCompletionGate gate = service(Optional.of(summary(5, false)))
        .evaluate(7L, session(PracticeProgressStatus.IN_PROGRESS));

    assertThat(gate.canComplete()).isFalse();
    assertThat(gate.reasonCode()).isEqualTo(PracticeCompletionGate.ReasonCode.LATEST_REVIEW_FAILED);
    assertThat(gate.latestScore()).contains(new BigDecimal("5.0"));
  }

  @Test
  void allowsWhenLatestReviewPassed() {
    PracticeCompletionGate gate = service(Optional.of(summary(7, true)))
        .evaluate(7L, session(PracticeProgressStatus.IN_PROGRESS));

    assertThat(gate.canComplete()).isTrue();
    assertThat(gate.reasonCode()).isEqualTo(PracticeCompletionGate.ReasonCode.PASSED);
  }

  @Test
  void alreadyCompletedDoesNotOfferCompletion() {
    PracticeCompletionGate gate = service(Optional.of(summary(7, true)))
        .evaluate(7L, session(PracticeProgressStatus.COMPLETED));

    assertThat(gate.canComplete()).isFalse();
    assertThat(gate.reasonCode()).isEqualTo(PracticeCompletionGate.ReasonCode.ALREADY_COMPLETED);
  }

  private PracticeCompletionGateService service(Optional<PracticeCodeReviewSummary> latest) {
    return new PracticeCompletionGateService(new FakeReviewRepository(latest));
  }

  private PracticeSession session(PracticeProgressStatus status) {
    return new PracticeSession(50L, 7L, 12L, 1, "two-sum", PracticeSessionStatus.ACTIVE, 100L, 200L,
        status, null, Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"), "zh-CN");
  }

  private PracticeCodeReviewSummary summary(int score, boolean passed) {
    return new PracticeCodeReviewSummary(90L, 2, "java", new BigDecimal(score + ".0"), passed,
        Instant.parse("2026-01-02T00:00:00Z"));
  }

  private record FakeReviewRepository(Optional<PracticeCodeReviewSummary> latest)
      implements PracticeCodeReviewRepository {
    @Override
    public PracticeCodeReview save(PracticeCodeReviewDraft draft) {
      throw new UnsupportedOperationException("save not used");
    }
    @Override
    public Optional<PracticeCodeReviewSummary> findLatestSummary(long userId, long sessionId) {
      return latest;
    }
    @Override
    public Optional<PracticeCodeReview> findLatest(long userId, long sessionId) {
      return Optional.empty();
    }
    @Override
    public List<PracticeCodeReviewSummary> findSummaries(long userId, long sessionId) {
      return List.of();
    }
    @Override
    public Optional<PracticeCodeReview> findById(long userId, long sessionId, long reviewId) {
      return Optional.empty();
    }
    @Override
    public Optional<PracticeCodeReview> findByUserMessage(long userId, long sessionId, long userMessageId) {
      return Optional.empty();
    }
  }
}
```

- [ ] **Step 2: Add failing session service assertions**

Modify `PracticeSessionServiceTest` to cover:

```java
assertThatThrownBy(() -> service.updateProgressStatus(7L, 50L, PracticeProgressStatus.COMPLETED))
    .isInstanceOfSatisfying(LearningPlanException.class, exception ->
        assertThat(exception.code()).isEqualTo("PRACTICE_COMPLETION_REVIEW_REQUIRED"));
```

Also add a passing case where fake latest review has score `7.0` and the progress update occurs.

- [ ] **Step 3: Run RED**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-application -am -Dtest=PracticeCompletionGateServiceTest,PracticeSessionServiceTest test
```

Expected: compilation fails because Review domain and gate service do not exist.

- [ ] **Step 4: Implement domain and repository port**

Implement these records with constructor validation and immutable collections:

```java
public final class PracticeCodeReviewConstants {
  public static final String SCENARIO = "practice_code_review";
  public static final String SCHEMA_NAME = "practice_code_review_result";
  public static final String SCHEMA_VERSION = "v1";
  public static final BigDecimal PASS_SCORE = new BigDecimal("6.0");
  public static final String METADATA_PRACTICE_CAPABILITIES = "practiceCapabilities";
  public static final String METADATA_CODE_REVIEW = "codeReview";
  public static final String EVIDENCE_CORRECTNESS_BLOCKING_CAP = "CORRECTNESS_BLOCKING_CAP";
  private PracticeCodeReviewConstants() {}
}
```

Create:

```java
public record PracticeCodeReviewEvidence(String type, String value) {}
public record PracticeCodeReviewScore(BigDecimal correctness, BigDecimal complexity, BigDecimal edgeCases,
    BigDecimal codeQuality, BigDecimal problemFit, BigDecimal total) {}
public record PracticeCodeReviewSummary(long id, int versionNo, String language, BigDecimal totalScore,
    boolean passed, Instant createdAt) {}
public record PracticeCompletionGate(boolean canComplete, ReasonCode reasonCode, String message,
    Optional<BigDecimal> latestScore, BigDecimal passScore) {
  public enum ReasonCode { NO_REVIEW, LATEST_REVIEW_FAILED, PASSED, ALREADY_COMPLETED }
}
```

Create `PracticeCodeReview`, `PracticeCodeReviewDraft`, and `PracticeCodeReviewRepository` with methods from the spec:

```java
PracticeCodeReview save(PracticeCodeReviewDraft draft);
Optional<PracticeCodeReviewSummary> findLatestSummary(long userId, long sessionId);
Optional<PracticeCodeReview> findLatest(long userId, long sessionId);
List<PracticeCodeReviewSummary> findSummaries(long userId, long sessionId);
Optional<PracticeCodeReview> findById(long userId, long sessionId, long reviewId);
Optional<PracticeCodeReview> findByUserMessage(long userId, long sessionId, long userMessageId);
```

- [ ] **Step 5: Implement completion gate service**

Create `PracticeCompletionGateService.java`:

```java
package org.congcong.algomentor.mentor.application.practice;

import java.math.BigDecimal;
import java.util.Optional;

public class PracticeCompletionGateService {

  private final PracticeCodeReviewRepository reviewRepository;

  public PracticeCompletionGateService(PracticeCodeReviewRepository reviewRepository) {
    this.reviewRepository = reviewRepository;
  }

  public PracticeCompletionGate evaluate(long userId, PracticeSession session) {
    if (session.progressStatus() == PracticeProgressStatus.COMPLETED) {
      return gate(false, PracticeCompletionGate.ReasonCode.ALREADY_COMPLETED, "该题目已经标记完成。", Optional.empty());
    }
    Optional<PracticeCodeReviewSummary> latest = reviewRepository.findLatestSummary(userId, session.id());
    if (latest.isEmpty()) {
      return gate(false, PracticeCompletionGate.ReasonCode.NO_REVIEW,
          "完成前需要先粘贴完整代码完成一次 AI Review。", Optional.empty());
    }
    BigDecimal score = latest.get().totalScore();
    if (score.compareTo(PracticeCodeReviewConstants.PASS_SCORE) < 0) {
      return gate(false, PracticeCompletionGate.ReasonCode.LATEST_REVIEW_FAILED,
          "最近一次 Review 为 %s/10，达到 6 分后可标记完成。".formatted(score.stripTrailingZeros().toPlainString()),
          Optional.of(score));
    }
    return gate(true, PracticeCompletionGate.ReasonCode.PASSED, "标记为已完成", Optional.of(score));
  }

  private PracticeCompletionGate gate(
      boolean canComplete,
      PracticeCompletionGate.ReasonCode reasonCode,
      String message,
      Optional<BigDecimal> latestScore
  ) {
    return new PracticeCompletionGate(canComplete, reasonCode, message, latestScore,
        PracticeCodeReviewConstants.PASS_SCORE);
  }
}
```

- [ ] **Step 6: Integrate session service**

Modify `PracticeSessionResult` to include:

```java
PracticeCodeReviewSummary latestReview,
PracticeCompletionGate completionGate
```

Modify `PracticeSessionService` constructor to accept `PracticeCodeReviewRepository` and create a `PracticeCompletionGateService`. In `result(...)`, compute latest review and gate. In `updateProgressStatus(...)`, before updating progress:

```java
PracticeCompletionGate gate = completionGateService.evaluate(userId, session);
if (!gate.canComplete()) {
  throw switch (gate.reasonCode()) {
    case NO_REVIEW -> new LearningPlanException("PRACTICE_COMPLETION_REVIEW_REQUIRED", gate.message());
    case LATEST_REVIEW_FAILED -> new LearningPlanException("PRACTICE_COMPLETION_REVIEW_NOT_PASSED", gate.message());
    case ALREADY_COMPLETED -> new LearningPlanException("PRACTICE_PROGRESS_ALREADY_COMPLETED", gate.message());
    case PASSED -> new IllegalStateException("Passed gate cannot block completion");
  };
}
```

- [ ] **Step 7: Run GREEN**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-application -am -Dtest=PracticeCompletionGateServiceTest,PracticeSessionServiceTest test
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice \
  backend/mentor-application/src/test/java/org/congcong/algomentor/mentor/application/practice
git commit -m "feat: add practice review completion gate"
```

### Task 4: Practice Code Review Persistence

**Files:**
- Create migration, mapper, row, repository files listed under “Practice API and persistence”
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/config/MentorApiMyBatisConfiguration.java`
- Test: `backend/mentor-api/src/test/java/org/congcong/algomentor/api/config/FlywayMigrationResourceTest.java`
- Test: `backend/mentor-api/src/test/java/org/congcong/algomentor/api/practice/repository/MyBatisPracticeCodeReviewRepositoryTest.java`
- Test: `backend/mentor-api/src/test/java/org/congcong/algomentor/api/practice/mapper/PracticeCodeReviewMapperXmlTest.java`

- [ ] **Step 1: Write failing mapper XML test**

Create `PracticeCodeReviewMapperXmlTest`:

```java
package org.congcong.algomentor.api.practice.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Reader;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class PracticeCodeReviewMapperXmlTest {

  @Test
  void mybatisLoadsPracticeCodeReviewMapperXml() throws Exception {
    Configuration configuration = new Configuration();
    configuration.setMapUnderscoreToCamelCase(true);

    try (Reader reader = Resources.getResourceAsReader("mapper/practice/PracticeCodeReviewMapper.xml")) {
      new XMLMapperBuilder(
          reader,
          configuration,
          "mapper/practice/PracticeCodeReviewMapper.xml",
          configuration.getSqlFragments()).parse();
    }

    String namespace = "org.congcong.algomentor.api.practice.mapper.PracticeCodeReviewMapper.";
    assertThat(configuration.hasStatement(namespace + "insert")).isTrue();
    assertThat(configuration.hasStatement(namespace + "findLatest")).isTrue();
    assertThat(configuration.hasStatement(namespace + "findLatestSummary")).isTrue();
    assertThat(configuration.hasStatement(namespace + "findSummaries")).isTrue();
    assertThat(configuration.hasStatement(namespace + "findById")).isTrue();
    assertThat(configuration.hasStatement(namespace + "findByUserMessage")).isTrue();
  }
}
```

- [ ] **Step 2: Write failing repository mapping test**

Create `MyBatisPracticeCodeReviewRepositoryTest` with mocked mapper:

```java
@Test
void mapsJsonbRowsToDomain() {
  PracticeCodeReviewMapper mapper = mock(PracticeCodeReviewMapper.class);
  when(mapper.findLatest(7L, 50L)).thenReturn(fullRow());
  MyBatisPracticeCodeReviewRepository repository = new MyBatisPracticeCodeReviewRepository(mapper, new ObjectMapper());

  PracticeCodeReview review = repository.findLatest(7L, 50L).orElseThrow();

  assertThat(review.evidence()).contains(new PracticeCodeReviewEvidence("ENTRY_FUNCTION", "twoSum"));
  assertThat(review.deductionReasons()).containsExactly("边界条件不足");
  assertThat(review.score().total()).isEqualByComparingTo("7.0");
}
```

Also cover `findById` empty for mapper null and `save` returning inserted row. Use `ObjectMapper.valueToTree(...)` for JSON fields.

- [ ] **Step 3: Update Flyway migration test expectation**

Modify `FlywayMigrationResourceTest`:

```java
.containsKeys("8", "10", "12", "13");
```

- [ ] **Step 4: Run RED**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-api -am -Dtest=PracticeCodeReviewMapperXmlTest,MyBatisPracticeCodeReviewRepositoryTest,FlywayMigrationResourceTest test
```

Expected: missing migration/mapper/classes fail.

- [ ] **Step 5: Add migration**

Create `V13__practice_code_review_schema.sql`:

```sql
CREATE TABLE IF NOT EXISTS practice_code_review (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  plan_id BIGINT NOT NULL,
  phase_index INTEGER NOT NULL,
  problem_slug VARCHAR(255) NOT NULL,
  practice_session_id BIGINT NOT NULL REFERENCES practice_session(id) ON DELETE CASCADE,
  version_no INTEGER NOT NULL,
  user_message_id BIGINT NOT NULL REFERENCES agent_message(id),
  assistant_message_id BIGINT NULL REFERENCES agent_message(id),
  agent_run_id BIGINT NULL REFERENCES agent_run(id),
  raw_code TEXT NOT NULL,
  normalized_code TEXT NOT NULL,
  language VARCHAR(64) NOT NULL,
  detection_evidence_json JSONB NOT NULL DEFAULT '[]'::JSONB,
  context_summary TEXT NOT NULL,
  total_score NUMERIC(4, 1) NOT NULL,
  correctness_score NUMERIC(3, 1) NOT NULL,
  complexity_score NUMERIC(3, 1) NOT NULL,
  edge_case_score NUMERIC(3, 1) NOT NULL,
  code_quality_score NUMERIC(3, 1) NOT NULL,
  problem_fit_score NUMERIC(3, 1) NOT NULL,
  passed BOOLEAN NOT NULL,
  deduction_reasons_json JSONB NOT NULL DEFAULT '[]'::JSONB,
  improvement_suggestions_json JSONB NOT NULL DEFAULT '[]'::JSONB,
  review_markdown TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uk_practice_code_review_session_version UNIQUE (practice_session_id, version_no),
  CONSTRAINT uk_practice_code_review_user_message UNIQUE (practice_session_id, user_message_id),
  CONSTRAINT ck_practice_code_review_score CHECK (
    total_score >= 0 AND total_score <= 10
    AND correctness_score >= 0 AND correctness_score <= 4
    AND complexity_score >= 0 AND complexity_score <= 2
    AND edge_case_score >= 0 AND edge_case_score <= 2
    AND code_quality_score >= 0 AND code_quality_score <= 1
    AND problem_fit_score >= 0 AND problem_fit_score <= 1
  )
);

CREATE INDEX IF NOT EXISTS idx_practice_code_review_latest
  ON practice_code_review(practice_session_id, version_no DESC);

CREATE INDEX IF NOT EXISTS idx_practice_code_review_user_problem
  ON practice_code_review(user_id, plan_id, phase_index, problem_slug, created_at DESC);
```

- [ ] **Step 6: Implement mapper and repository**

Mapper SQL must lock the session row during insert to assign version:

```xml
<select id="insert" resultMap="PracticeCodeReviewRowMap" affectData="true">
  WITH locked_session AS (
    SELECT id
    FROM practice_session
    WHERE id = #{practiceSessionId}
      AND user_id = #{userId}
    FOR UPDATE
  ),
  next_version AS (
    SELECT COALESCE(MAX(version_no), 0) + 1 AS version_no
    FROM practice_code_review
    WHERE practice_session_id = #{practiceSessionId}
  )
  INSERT INTO practice_code_review (...)
  SELECT ..., next_version.version_no, ...
  FROM locked_session, next_version
  ON CONFLICT (practice_session_id, user_message_id) DO UPDATE
    SET raw_code = practice_code_review.raw_code
  RETURNING ...
</select>
```

The no-op conflict update returns the existing review row for an idempotent retry without adding an `updated_at` column to the table. Prefer row JSON fields as `JsonNode` and convert in `MyBatisPracticeCodeReviewRepository` with `ObjectMapper`.

- [ ] **Step 7: Wire MyBatis config**

Add beans:

```java
@Bean
@ConditionalOnMissingBean
public PracticeCodeReviewMapper practiceCodeReviewMapper(SqlSessionTemplate sqlSessionTemplate) {
  return sqlSessionTemplate.getMapper(PracticeCodeReviewMapper.class);
}

@Bean
@ConditionalOnMissingBean(PracticeCodeReviewRepository.class)
public PracticeCodeReviewRepository practiceCodeReviewRepository(
    PracticeCodeReviewMapper mapper,
    ObjectMapper objectMapper
) {
  return new MyBatisPracticeCodeReviewRepository(mapper, objectMapper);
}
```

- [ ] **Step 8: Run GREEN**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-api -am -Dtest=PracticeCodeReviewMapperXmlTest,MyBatisPracticeCodeReviewRepositoryTest,FlywayMigrationResourceTest test
```

Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add backend/mentor-api/src/main/resources/db/migration/V13__practice_code_review_schema.sql \
  backend/mentor-api/src/main/java/org/congcong/algomentor/api/practice \
  backend/mentor-api/src/main/resources/mapper/practice/PracticeCodeReviewMapper.xml \
  backend/mentor-api/src/main/java/org/congcong/algomentor/api/config/MentorApiMyBatisConfiguration.java \
  backend/mentor-api/src/test/java/org/congcong/algomentor/api
git commit -m "feat: persist practice code reviews"
```

### Task 5: Structured Review Mapper and Prompt

**Files:**
- Create: `PracticeCodeReviewJsonSchema.java`
- Create: `PracticeCodeReviewPromptBuilder.java`
- Create: `PracticeCodeReviewStructuredOutputMapper.java`
- Test: `backend/mentor-application/src/test/java/org/congcong/algomentor/mentor/application/practice/PracticeCodeReviewStructuredOutputMapperTest.java`
- Test: `backend/mentor-application/src/test/java/org/congcong/algomentor/mentor/application/practice/PracticeCodeReviewJsonSchemaTest.java`

- [ ] **Step 1: Write failing mapper tests**

Create tests for:

```java
@Test
void normalizesTotalAndPassedFromScores()
@Test
void rejectsCompleteSubmissionWithEmptyCode()
@Test
void returnsNotCompleteWhenModelRejectsCurrentProblem()
@Test
void capsTotalAtFiveWhenCorrectnessIsBlocking()
@Test
void rejectsScoreAboveDimensionLimit()
```

Use `ObjectMapper.readTree(...)` with JSON examples:

```json
{
  "isCodeSubmission": true,
  "belongsToCurrentProblem": true,
  "isCompleteLeetCodeSolution": true,
  "language": "java",
  "rawCode": "class Solution { int climbStairs(int n) { return n; } }",
  "normalizedCode": "class Solution { public int climbStairs(int n) { return n; } }",
  "evidence": [{"type": "ENTRY_FUNCTION", "value": "climbStairs"}],
  "contextSummary": "用户提交了 Java 解法。",
  "scores": {
    "correctness": 3.0,
    "complexity": 2.0,
    "edgeCases": 1.0,
    "codeQuality": 1.0,
    "problemFit": 1.0,
    "total": 8.8
  },
  "passed": false,
  "deductionReasons": ["边界覆盖不足"],
  "improvementSuggestions": ["补充 n=1 的处理"],
  "reviewMarkdown": "整体可改进。"
}
```

- [ ] **Step 2: Run RED**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-application -am -Dtest=PracticeCodeReviewStructuredOutputMapperTest,PracticeCodeReviewJsonSchemaTest test
```

Expected: missing classes fail.

- [ ] **Step 3: Implement JSON Schema**

Mirror the style of `LearningPlanDraftJsonSchema`. Required top-level fields:

```java
isCodeSubmission, belongsToCurrentProblem, isCompleteLeetCodeSolution, language,
rawCode, normalizedCode, evidence, contextSummary, scores, passed,
deductionReasons, improvementSuggestions, reviewMarkdown
```

Set `additionalProperties=false`. Score numeric ranges:

```text
correctness 0..4
complexity 0..2
edgeCases 0..2
codeQuality 0..1
problemFit 0..1
total 0..10
```

- [ ] **Step 4: Implement output mapper**

Mapper method:

```java
PracticeReviewResult map(PracticeTurnContext context, JsonNode structuredOutput)
```

Rules:

- If any of `isCodeSubmission`, `belongsToCurrentProblem`, `isCompleteLeetCodeSolution` is false, return `NOT_COMPLETE_SUBMISSION`.
- If complete submission but `rawCode` or `normalizedCode` blank, return `FAILED` with failure code `INVALID_STRUCTURED_OUTPUT`.
- Validate dimension bounds. Invalid dimensions return `FAILED`, not a saved draft.
- Normalize total to the sum of dimensions.
- If `correctness <= 2` and total > 5, cap total to `5.0` and append evidence `CORRECTNESS_BLOCKING_CAP`.
- Set `passed = total >= 6`.

- [ ] **Step 5: Implement prompt builder**

`PracticeCodeReviewPromptBuilder.build(PracticeTurnContext context)` returns `List<LlmMessage>` with:

- system role: algorithm code review assistant, safety/privacy rules.
- user role: current problem facts, learning plan facts, extracted code, original message, recent chat summary, scoring rubric.

Do not log or place secrets in metadata. Keep the code only in prompt messages.

- [ ] **Step 6: Run GREEN**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-application -am -Dtest=PracticeCodeReviewStructuredOutputMapperTest,PracticeCodeReviewJsonSchemaTest test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeCodeReviewJsonSchema.java \
  backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeCodeReviewPromptBuilder.java \
  backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeCodeReviewStructuredOutputMapper.java \
  backend/mentor-application/src/test/java/org/congcong/algomentor/mentor/application/practice
git commit -m "feat: map structured practice code reviews"
```

### Task 6: Turn Classifier and Capability Service

**Files:**
- Create turn/capability/review service files listed under “Practice turn orchestration and Review”
- Test: `PracticeTurnClassifierTest.java`
- Test: `PracticeCodeReviewServiceTest.java`
- Test: `CodeReviewTurnCapabilityTest.java`

- [ ] **Step 1: Write failing classifier tests**

Create `PracticeTurnClassifierTest` covering:

```java
detectsJavaClassSolution()
detectsPythonFunctionSubmission()
extractsMarkdownFencedCodeLanguage()
rejectsPlainQuestion()
rejectsStackTraceOnly()
rejectsPseudocodeWithoutCodeShape()
```

Use assertions:

```java
PracticeTurnClassification result = classifier.classify("```java\nclass Solution { ... }\n```", problemDetail);
assertThat(result.codeSubmissionCandidate()).isTrue();
assertThat(result.languageHint()).isEqualTo("java");
assertThat(result.extractedCode()).contains("class Solution");
assertThat(result.evidence()).extracting(PracticeCodeReviewEvidence::type).contains("FENCED_CODE_BLOCK");
```

- [ ] **Step 2: Write failing service/capability tests**

Tests:

- `CodeReviewTurnCapabilityTest.nonCandidateReturnsNotCodeLikeAndDoesNotCallService`
- `CodeReviewTurnCapabilityTest.failureIsolatedAsFailedResult`
- `PracticeCodeReviewServiceTest.savesCompleteSubmission`
- `PracticeCodeReviewServiceTest.nonCurrentProblemDoesNotSave`
- `PracticeCodeReviewServiceTest.replayReturnsExistingReviewWithoutCallingLlm`
- `PracticeCodeReviewServiceTest.llmFailureReturnsFailed`

Use fake `LlmGateway`, fake repository, and a minimal `PracticeTurnContext` fixture.

- [ ] **Step 3: Run RED**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-application -am -Dtest=PracticeTurnClassifierTest,PracticeCodeReviewServiceTest,CodeReviewTurnCapabilityTest test
```

Expected: missing classes fail.

- [ ] **Step 4: Implement classifier**

Heuristics:

- Markdown fenced code block captures language and code.
- Java: `class Solution`, `public`, `return`, braces and semicolons.
- Python: `def `, indentation, `return`.
- JavaScript/TypeScript: `function`, `const`, `=>`, `return`.
- Reject messages dominated by stack trace/log markers: `Exception`, `Traceback`, `at `, `Runtime Error`, `Compile Error` without solution structure.
- Evidence types: `FENCED_CODE_BLOCK`, `CLASS_SOLUTION`, `FUNCTION_SIGNATURE`, `RETURN_STATEMENT`, `LANGUAGE_HINT`, `PROBLEM_SLUG_OR_TITLE`.

- [ ] **Step 5: Implement review result and capability**

`PracticeReviewStatus` enum:

```java
NOT_CODE_LIKE, NOT_COMPLETE_SUBMISSION, SAVED, FAILED
```

`PracticeTurnCapabilityResult` includes:

```java
String capabilityName;
PracticeReviewStatus status;
Map<String, Object> metadata;
```

`CodeReviewTurnCapability.afterTurn` catches `RuntimeException` and returns status `FAILED` with low-cardinality failure metadata only.

- [ ] **Step 6: Implement PracticeCodeReviewService**

Use:

```java
LlmCompletionRequest.builder()
  .modelSelector(LlmModelSelector.requiring(Set.of(LlmCapability.JSON_SCHEMA_OUTPUT)))
  .messages(promptBuilder.build(context))
  .responseFormat(new LlmResponseFormat.JsonSchema(
      PracticeCodeReviewConstants.SCHEMA_NAME,
      PracticeCodeReviewJsonSchema.schema(),
      true))
  .metadata(Map.of(
      PracticeCodeReviewConstants.METADATA_REVIEW_CANDIDATE, true,
      PracticeChatPromptConstants.METADATA_PRACTICE_SESSION_ID, context.session().id()))
  .build();
```

On idempotent replay, or before LLM if `findByUserMessage(...)` exists, return `SAVED` for the existing review. On mapper `SAVED`, call `repository.save(draft)`.

- [ ] **Step 7: Add metrics hooks**

Inject `MeterRegistry` as optional or use `Metrics.globalRegistry` only if project pattern allows. Count:

- candidate
- saved
- not complete
- failed

Timer around Review LLM/service duration. Tags: `language`, `passed`, `failureCode`.

- [ ] **Step 8: Run GREEN**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-application -am -Dtest=PracticeTurnClassifierTest,PracticeCodeReviewServiceTest,CodeReviewTurnCapabilityTest test
```

Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice \
  backend/mentor-application/src/test/java/org/congcong/algomentor/mentor/application/practice
git commit -m "feat: add practice code review capability"
```

### Task 7: Practice Turn Orchestrator and SSE Metadata

**Files:**
- Create: `PracticeTurnOrchestrator.java`
- Modify: `PracticeMessageStreamService.java`
- Modify: `PracticeChatPromptSectionProvider.java`
- Modify: `AgentConversationApiAutoConfiguration.java`
- Test: `PracticeTurnOrchestratorTest.java`
- Test: `PracticeMessageStreamServiceTest.java`
- Test: `PracticeChatPromptSectionProviderTest.java`

- [ ] **Step 1: Write failing orchestrator tests**

Test cases:

```java
forwardsRunEndWithCapabilityMetadata()
preservesNonRunEndEventsBeforeRunEnd()
touchesSessionAfterMergedRunEnd()
missingTurnMessagesReturnsFailedCapabilityMetadata()
idempotentReplayDoesNotDuplicateReview()
capabilityExceptionStillForwardsRunEnd()
```

Expected metadata shape:

```java
assertThat(runEnd.metadata()).containsKey("practiceCapabilities");
assertThat(((Map<?, ?>) ((Map<?, ?>) runEnd.metadata().get("practiceCapabilities")).get("codeReview")))
    .containsEntry("status", "SAVED")
    .containsEntry("reviewId", 123L)
    .containsEntry("versionNo", 2)
    .containsEntry("totalScore", "7.0")
    .containsEntry("passed", true);
```

- [ ] **Step 2: Run RED**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-application -am -Dtest=PracticeTurnOrchestratorTest,PracticeMessageStreamServiceTest,PracticeChatPromptSectionProviderTest test
```

Expected: orchestrator missing and existing service tests fail after constructor change.

- [ ] **Step 3: Implement orchestrator**

Constructor dependencies:

```java
PracticeSessionRepository sessionRepository
AgentConversationRunCoordinator coordinator
AgentTurnMessageLookupRepository turnMessageLookupRepository
PracticeTurnClassifier classifier
PracticeTurnCapabilityRegistry capabilityRegistry
```

`stream(...)` flow:

1. Validate session active and `agentTaskId`.
2. Compute locale and metadata.
3. Classify current message.
4. Build `AgentConversationCommand`.
5. Subscribe to coordinator publisher.
6. For every event except `AgentRunEnd`, forward as-is.
7. On `AgentRunEnd`, use `AgentRuntimeMetadataKeys.RUN_DB_ID` to find turn messages.
8. Build `PracticeTurnContext`.
9. Execute capabilities.
10. Merge metadata under `practiceCapabilities`.
11. Forward new `AgentRunEnd`.

Never log full user message or code.

- [ ] **Step 4: Modify PracticeMessageStreamService**

Make it delegate to `PracticeTurnOrchestrator` and keep touch-on-run-end behavior around the merged publisher. It should no longer directly construct `AgentConversationCommand`.

- [ ] **Step 5: Update chat prompt policy**

Modify `PracticeChatPromptSectionProvider.COACHING_POLICY` with the spec rule:

```text
如果当前用户消息看起来是完整代码提交，请按代码 Review 风格回复，覆盖正确性、复杂度、边界条件、代码质量和下一步建议。
如果只是片段、报错或伪代码，请正常答疑，并引导用户粘贴完整 LeetCode Solution 代码生成正式 Review。
```

- [ ] **Step 6: Wire auto-configuration**

`AgentConversationApiAutoConfiguration` creates:

- `PracticeTurnClassifier`
- `PracticeCodeReviewPromptBuilder`
- `PracticeCodeReviewStructuredOutputMapper`
- `PracticeCodeReviewService`
- `CodeReviewTurnCapability`
- `PracticeTurnCapabilityRegistry`
- `PracticeTurnOrchestrator`
- `PracticeMessageStreamService` with orchestrator
- `PracticeSessionService` with review repository

Use `@ConditionalOnBean` for `LlmGateway`, `PracticeCodeReviewRepository`, `AgentTurnMessageLookupRepository`.

- [ ] **Step 7: Run GREEN**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-application,mentor-api -am -Dtest=PracticeTurnOrchestratorTest,PracticeMessageStreamServiceTest,PracticeChatPromptSectionProviderTest test
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add backend/mentor-application backend/mentor-api/src/main/java/org/congcong/algomentor/mentor/api/autoconfigure/AgentConversationApiAutoConfiguration.java
git commit -m "feat: orchestrate practice review after chat runs"
```

### Task 8: Practice Review API

**Files:**
- Modify/create API response and controller files listed under “Practice API and persistence”
- Test: `backend/mentor-api/src/test/java/org/congcong/algomentor/api/controller/practice/PracticeSessionControllerTest.java`

- [ ] **Step 1: Write failing controller tests**

Add tests:

```java
sessionResponseIncludesLatestReviewAndCompletionGate()
reviewHistoryEndpointReturnsReviewsAndGate()
reviewDetailEndpointReturnsReviewBody()
reviewHistoryRequiresAuthentication()
completionBlockedByNoReviewReturnsStableCode()
completionBlockedByFailedReviewReturnsStableCode()
```

Expected JSON snippets:

```java
.andExpect(jsonPath("$.data.latestReview.totalScore").value(7.0))
.andExpect(jsonPath("$.data.completionGate.reasonCode").value("PASSED"))
.andExpect(jsonPath("$.data.reviews[0].versionNo").value(2))
.andExpect(jsonPath("$.data.scores.correctness").value(3.0))
```

- [ ] **Step 2: Run RED**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-api -am -Dtest=PracticeSessionControllerTest test
```

Expected: response fields/endpoints missing.

- [ ] **Step 3: Add response records and mapper**

Create response records:

```java
public record PracticeCompletionGateResponse(boolean canComplete, String reasonCode, String message,
    BigDecimal latestScore, BigDecimal passScore) {}
public record PracticeCodeReviewSummaryResponse(long id, int versionNo, String language,
    BigDecimal totalScore, boolean passed, Instant createdAt) {}
public record PracticeCodeReviewHistoryResponse(PracticeCodeReviewSummaryResponse latestReview,
    List<PracticeCodeReviewSummaryResponse> reviews, PracticeCompletionGateResponse completionGate) {}
```

Detail includes all fields from spec.

Update `PracticeSessionResponse` to include `latestReview` and `completionGate`.

- [ ] **Step 4: Add API constants and controller endpoints**

Constants:

```java
public static final String PRACTICE_SESSION_REVIEWS_PATH = "/{sessionId}/reviews";
public static final String PRACTICE_SESSION_REVIEW_DETAIL_PATH = "/{sessionId}/reviews/{reviewId}";
```

Controller:

```java
@GetMapping(ApiContractConstants.PRACTICE_SESSIONS_BASE_PATH + ApiContractConstants.PRACTICE_SESSION_REVIEWS_PATH)
public ApiResponse<PracticeCodeReviewHistoryResponse> reviews(@PathVariable long sessionId) { ... }
```

Use current user ID and `PracticeSessionService.reviewHistory(...)` or repository + gate service through service methods. Keep controller thin.

- [ ] **Step 5: Add session service review methods**

Add:

```java
PracticeCodeReviewHistory history(long userId, long sessionId);
PracticeCodeReview detail(long userId, long sessionId, long reviewId);
```

These methods must first verify the session belongs to `userId` and then query review repository with `userId`.

- [ ] **Step 6: Run GREEN**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-api -am -Dtest=PracticeSessionControllerTest test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/mentor-api/src/main/java/org/congcong/algomentor/api \
  backend/mentor-api/src/test/java/org/congcong/algomentor/api/controller/practice/PracticeSessionControllerTest.java \
  backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice
git commit -m "feat: expose practice code review APIs"
```

### Task 9: Frontend API Types and Review Components

**Files:**
- Modify: `frontend/src/types/api.ts`
- Modify: `frontend/src/services/api.ts`
- Modify: `frontend/src/i18n/locales.ts`
- Create Review component files
- Test: `frontend/src/learning-plans/PracticeChatWorkbench.test.tsx`

- [ ] **Step 1: Write failing frontend tests for types/UI behavior**

Create `PracticeChatWorkbench.test.tsx` and mock `../services/api` to cover:

```text
renders no-review gate and disables completion
shows review drawer empty state
shows review versions and loads selected detail
```

Use test fixtures with `completionGate` and `latestReview`.

- [ ] **Step 2: Run RED**

Run:

```bash
npm --cache ./.npm --prefix frontend test -- --run PracticeChatWorkbench.test.tsx
```

Expected: missing types/components fail.

- [ ] **Step 3: Extend TypeScript contracts**

Add to `frontend/src/types/api.ts`:

```ts
export type PracticeCompletionGateReasonCode =
  | 'NO_REVIEW'
  | 'LATEST_REVIEW_FAILED'
  | 'PASSED'
  | 'ALREADY_COMPLETED';

export interface PracticeCompletionGate {
  canComplete: boolean;
  reasonCode: PracticeCompletionGateReasonCode;
  message: string;
  latestScore?: number | null;
  passScore: number;
}

export interface PracticeCodeReviewSummary {
  id: number;
  versionNo: number;
  language: string;
  totalScore: number;
  passed: boolean;
  createdAt: string;
}
```

Add score/evidence/history/detail interfaces and extend `PracticeSessionResponse`.

- [ ] **Step 4: Add API functions**

In `frontend/src/services/api.ts`:

```ts
export async function getPracticeSessionReviews(
  sessionId: number,
  signal?: AbortSignal,
): Promise<ApiResponse<PracticeCodeReviewHistoryResponse>> { ... }

export async function getPracticeSessionReviewDetail(
  sessionId: number,
  reviewId: number,
  signal?: AbortSignal,
): Promise<ApiResponse<PracticeCodeReviewDetail>> { ... }
```

- [ ] **Step 5: Add i18n keys**

Add Chinese and English keys for:

- review empty state
- review loading/error
- passed/failed labels
- version label
- code snapshot
- deduction reasons
- improvement suggestions
- context summary
- completion gate fallback
- new composer placeholder and hint

- [ ] **Step 6: Create review components**

Keep components focused:

- `ReviewScoreBadge`: visual score/pass status.
- `CompletionGateHint`: renders backend message or fallback.
- `ReviewVersionList`: version list with selected state.
- `ReviewDetailPanel`: Markdown review, code `pre`, lists.
- `ReviewHistoryDrawer`: history list, detail loading, empty/error states.

Use lucide icons only where helpful; avoid cards inside cards.

- [ ] **Step 7: Run GREEN for component tests**

Run:

```bash
npm --cache ./.npm --prefix frontend test -- --run PracticeChatWorkbench.test.tsx
```

Expected: PASS for new component tests that do not require stream integration yet.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/types/api.ts frontend/src/services/api.ts frontend/src/i18n/locales.ts \
  frontend/src/learning-plans/ReviewHistoryDrawer.tsx \
  frontend/src/learning-plans/ReviewScoreBadge.tsx \
  frontend/src/learning-plans/ReviewVersionList.tsx \
  frontend/src/learning-plans/ReviewDetailPanel.tsx \
  frontend/src/learning-plans/CompletionGateHint.tsx \
  frontend/src/learning-plans/PracticeChatWorkbench.test.tsx
git commit -m "feat: add practice review frontend contracts"
```

### Task 10: Frontend Workbench Integration

**Files:**
- Modify: `frontend/src/learning-plans/PracticeChatWorkbench.tsx`
- Modify: `frontend/src/styles.css`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/learning-plans/PracticeChatWorkbench.test.tsx`

- [ ] **Step 1: Add failing integration tests**

Extend `PracticeChatWorkbench.test.tsx`:

```text
refreshes messages session and reviews after agent_run_end
refreshes session and reviews when active run polling clears
shows backend completion gate error
enables completion when latest review passes
keeps completion disabled for latest failed review
```

Update `App.test.tsx` fixtures to include default:

```ts
completionGate: {
  canComplete: false,
  reasonCode: 'NO_REVIEW',
  message: '完成前需要先粘贴完整代码完成一次 AI Review。',
  passScore: 6,
}
```

- [ ] **Step 2: Run RED**

Run:

```bash
npm --cache ./.npm --prefix frontend test -- --run PracticeChatWorkbench.test.tsx App.test.tsx
```

Expected: current workbench still has Review placeholder and stale refresh behavior.

- [ ] **Step 3: Integrate state and refresh**

In `PracticeChatWorkbench.tsx`:

- Store `reviewHistory`, `reviewHistoryLoading`, `reviewHistoryError`.
- Initial session load sets messages, latest review, gate from session response.
- When drawer opens, call `getPracticeSessionReviews`.
- After `agent_run_end`, call `refreshMessages`, `refreshSession`, `refreshReviews`.
- When active-run polling clears, refresh messages, session, and reviews.
- Guard all refreshes with the existing load token/session id checks.

- [ ] **Step 4: Integrate completion gate UI**

Completion button should be visible when session exists and progress is not `COMPLETED`. Disable when:

```ts
!sessionResponse.completionGate.canComplete
|| completionUpdating
|| status === 'loading'
|| status === 'streaming'
|| hasActiveRun
```

Render `CompletionGateHint` next to or below the button using backend `completionGate.message`.

- [ ] **Step 5: Replace Review placeholder**

Replace:

```tsx
<aside className="practice-review-panel">...</aside>
```

with `ReviewHistoryDrawer` using fetched history/detail APIs. Place the drawer as a sibling of `.practice-message-list` inside `.practice-workbench`, so opening the drawer does not insert a fake chat message or disturb message auto-scroll.

- [ ] **Step 6: Add styles**

Append CSS for:

- `.practice-review-drawer`
- `.review-score-badge`
- `.review-version-list`
- `.review-detail-panel`
- `.completion-gate-hint`

Use constrained `pre` blocks with `overflow-x: auto`, stable dimensions for list rows, and mobile rules. Do not rewrite existing exact CSS snippets asserted by `styles.test.tsx`.

- [ ] **Step 7: Run GREEN**

Run:

```bash
npm --cache ./.npm --prefix frontend test -- --run PracticeChatWorkbench.test.tsx App.test.tsx styles.test.tsx
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/learning-plans/PracticeChatWorkbench.tsx frontend/src/styles.css \
  frontend/src/App.test.tsx frontend/src/learning-plans/PracticeChatWorkbench.test.tsx
git commit -m "feat: show practice review history and completion gate"
```

### Task 11: End-to-End Backend Verification and Wiring Cleanup

**Files:**
- Modify only backend files touched by previous tasks when verification exposes a concrete compile, wiring, mapper, or test failure.
- Test: targeted suites plus module tests.

- [ ] **Step 1: Run targeted backend test suites**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository \
  -pl agent-core,agent-persistence-postgres,mentor-application,mentor-api -am test \
  -Dtest=AgentTurnMessagesTest,PostgresAgentTurnMessageLookupRepositoryTest,AgentMapperXmlTest,PracticeCompletionGateServiceTest,PracticeSessionServiceTest,PracticeCodeReviewMapperXmlTest,MyBatisPracticeCodeReviewRepositoryTest,FlywayMigrationResourceTest,PracticeCodeReviewStructuredOutputMapperTest,PracticeCodeReviewJsonSchemaTest,PracticeTurnClassifierTest,PracticeCodeReviewServiceTest,CodeReviewTurnCapabilityTest,PracticeTurnOrchestratorTest,PracticeMessageStreamServiceTest,PracticeChatPromptSectionProviderTest,PracticeSessionControllerTest
```

Expected: PASS.

- [ ] **Step 2: Run backend test aggregate**

Run:

```bash
make backend-test
```

Expected: PASS.

- [ ] **Step 3: Fix integration failures**

If failures appear:

- Constructor/wiring failures: update `AgentConversationApiAutoConfiguration` and test configs.
- MyBatis ambiguity: ensure one `SqlSessionFactory` owns mapper scanning in the running context and bean names are not duplicated.
- Migration conflict: re-run `find backend -path '*/db/migration*' -type f | sort` and bump V13 only if another migration already claimed it.
- JSONB mapper issue: keep mapper rows as `JsonNode` and conversion in repository.

- [ ] **Step 4: Commit fixes**

```bash
git add backend
git commit -m "test: verify practice code review backend"
```

### Task 12: Full Verification

**Files:**
- No planned source changes unless verification finds issues.

- [ ] **Step 1: Run frontend tests**

Run:

```bash
npm --cache ./.npm --prefix frontend test -- --run
```

Expected: PASS.

- [ ] **Step 2: Run full build**

Run:

```bash
make build
```

Expected: PASS.

- [ ] **Step 3: Inspect git status**

Run:

```bash
git status --short
```

Expected: only intentional changes remain, with no generated `target`, `dist`, `.m2`, `.npm`, or worktree artifacts tracked.

- [ ] **Step 4: Commit verification fixes**

If verification required small fixes, commit them:

```bash
git add backend frontend docs
git commit -m "chore: finalize practice code review"
```

---

## Plan Self-Review Notes

- Spec coverage: persistence, orchestration, structured output, completion gate, API, frontend, privacy, metrics, idempotency, and verification are covered by tasks.
- Worktree: execution setup explicitly requires `superpowers:using-git-worktrees` before implementation.
- Subagents: plan header requires `subagent-driven-development` and high model level for subagents.
- No placeholders: all tasks include exact paths, commands, expected outcomes, and implementation contracts.
- Known risk: exact repository SQL for idempotent insert may need either `DO NOTHING + fallback` or a harmless conflict update; implementation must test duplicate user message behavior.
