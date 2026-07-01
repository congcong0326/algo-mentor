# Learning Plan Proposal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有学习计划能力上增加统一的 AI 提案机制，支持草案自然语言修订、正式计划扩展提案、扩展调整、应用和放弃。

**Architecture:** `mentor-application` 拥有提案组、版本、校验、prompt 和应用编排；`mentor-api` 负责 HTTP、SSE、MyBatis、Flyway 和响应映射。草案 revision 可以覆盖当前 `learning_plan_draft.draft_plan_json`，正式计划扩展只能通过 append-only repository 追加阶段和题目，并在同一事务内更新 `learning_plan.plan_json` 快照。前端通过独立 SSE 事件消费草案修订和扩展提案，不再使用“编辑目标摘要 + 特殊前缀消息”的旧路径。

**Tech Stack:** Java 17, Spring MVC, Maven, MyBatis, PostgreSQL/Flyway, AgentLoopRunner, provider-native JSON Schema, SSE, React 19, TypeScript, Vite, Vitest/React Testing Library.

---

## Review Result

设计文档 `docs/superpowers/specs/2026-07-01-learning-plan-proposal-design.md` 可以进入实施计划。核心判断和当前代码吻合：

- `MyBatisLearningPlanRepository.save(...)` 现在会调用 `deletePlanPhases(planId)` 后重建明细，正式计划扩展必须新增 append-only 写入路径。
- 草案修订复用 `LearningPlanDraftStructuredOutputMapper` 和 `LearningPlanDraftValidator` 可行，因为草案尚未绑定练习进度。
- 扩展版本需要单独结构化输出 `LearningPlanExtensionDraft`，不能输出完整替换计划。
- 前端现在确实存在 `followUpRegeneratePrefix(...)` 旧交互，需要被自然语言修订 SSE 替换。

实施时固定一个命名决策：设计文档 API 路径中的 `{proposalId}` 按“提案组 id”实现，具体版本 id 使用响应字段 `proposalId` 表达。这样 `POST /extension-proposals/{proposalGroupId}/revisions/stream` 和 `POST /extension-proposals/{proposalGroupId}/discard` 都能稳定指向同一组连续意图。

## Reference Specs

- `docs/superpowers/specs/2026-07-01-learning-plan-proposal-design.md`
- `docs/code-index.md`
- `docs/learning-plan-agent-stream-design.md`
- `docs/agent-structured-output-design.md`

## File Structure

### Application Domain

- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/proposal/LearningPlanProposalType.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/proposal/LearningPlanProposalTargetType.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/proposal/LearningPlanProposalGroupStatus.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/proposal/LearningPlanProposalRevisionStatus.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/proposal/LearningPlanProposalGroup.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/proposal/LearningPlanDraftRevision.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/proposal/LearningPlanExtensionRevision.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/proposal/LearningPlanExtensionDraft.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/proposal/LearningPlanProposalRepository.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/proposal/LearningPlanProposalGroupService.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/proposal/LearningPlanDraftRevisionResult.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/proposal/LearningPlanExtensionResult.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/proposal/LearningPlanExtensionApplyResult.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/proposal/LearningPlanExtensionValidator.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/proposal/LearningPlanProposalPromptBuilder.java`
- Modify: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/LearningPlanRepository.java`
- Modify: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeSessionRepository.java`

### Application Streams

- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/proposal/stream/LearningPlanProposalStreamEvent.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/proposal/stream/LearningPlanProposalEvent.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/proposal/stream/LearningPlanExtensionJsonSchema.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/proposal/stream/LearningPlanExtensionStructuredOutputMapper.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/proposal/stream/LearningPlanDraftRevisionStreamService.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/proposal/stream/LearningPlanExtensionProposalStreamService.java`
- Modify: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/stream/LearningPlanStreamConstants.java`

### API, Persistence, and Ops

- Create: `backend/mentor-api/src/main/resources/db/migration/V17__learning_plan_proposal_schema.sql`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/learningplan/mapper/model/LearningPlanProposalGroupRow.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/learningplan/mapper/model/LearningPlanDraftRevisionRow.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/learningplan/mapper/model/LearningPlanExtensionRevisionRow.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/learningplan/repository/MyBatisLearningPlanProposalRepository.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/learningplan/model/LearningPlanRevisionRequest.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/learningplan/model/LearningPlanDraftRevisionReadyResponse.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/learningplan/model/LearningPlanExtensionReadyResponse.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/learningplan/model/LearningPlanExtensionApplyResponse.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/learningplan/service/LearningPlanProposalStreamSseMapper.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/learningplan/service/SseLearningPlanProposalStreamSubscriber.java`
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/config/ApiContractConstants.java`
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/learningplan/LearningPlanController.java`
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/learningplan/config/LearningPlanConfiguration.java`
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/learningplan/mapper/LearningPlanMapper.java`
- Modify: `backend/mentor-api/src/main/resources/mapper/learningplan/LearningPlanMapper.xml`
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/learningplan/repository/MyBatisLearningPlanRepository.java`
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/practice/mapper/PracticeSessionMapper.java`
- Modify: `backend/mentor-api/src/main/resources/mapper/practice/PracticeSessionMapper.xml`
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/practice/repository/MyBatisPracticeSessionRepository.java`
- Modify: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/model/AiRunSource.java`
- Modify: `backend/ops-observability/src/main/java/org/congcong/algomentor/ops/observability/SseStreamType.java`
- Modify: `backend/ops-observability/src/main/java/org/congcong/algomentor/ops/observability/LearningOpsRecorder.java`
- Modify: `backend/ops-observability/src/main/java/org/congcong/algomentor/ops/observability/MicrometerOpsRecorders.java`
- Modify: `backend/ops-observability/src/main/java/org/congcong/algomentor/ops/observability/NoopOpsRecorders.java`

### Frontend

- Modify: `frontend/src/types/api.ts`
- Modify: `frontend/src/services/api.ts`
- Modify: `frontend/src/learning-plans/LearningPlanCreatePage.tsx`
- Modify: `frontend/src/learning-plans/LearningPlanDraftPanel.tsx`
- Modify: `frontend/src/learning-plans/LearningPlanDetail.tsx`
- Create: `frontend/src/learning-plans/LearningPlanExtensionPanel.tsx`
- Modify: `frontend/src/i18n/locales.ts`
- Modify: `frontend/src/styles.css`

### Tests

- Create: `backend/mentor-application/src/test/java/org/congcong/algomentor/mentor/application/learningplan/proposal/LearningPlanProposalGroupServiceTest.java`
- Create: `backend/mentor-application/src/test/java/org/congcong/algomentor/mentor/application/learningplan/proposal/LearningPlanExtensionValidatorTest.java`
- Create: `backend/mentor-application/src/test/java/org/congcong/algomentor/mentor/application/learningplan/proposal/LearningPlanExtensionApplyServiceTest.java`
- Create: `backend/mentor-application/src/test/java/org/congcong/algomentor/mentor/application/learningplan/proposal/stream/LearningPlanDraftRevisionStreamServiceTest.java`
- Create: `backend/mentor-application/src/test/java/org/congcong/algomentor/mentor/application/learningplan/proposal/stream/LearningPlanExtensionProposalStreamServiceTest.java`
- Modify: `backend/mentor-api/src/test/java/org/congcong/algomentor/api/controller/learningplan/LearningPlanControllerTest.java`
- Modify: `backend/mentor-api/src/test/java/org/congcong/algomentor/api/config/FlywayMigrationResourceTest.java`
- Modify: `backend/mentor-api/src/test/java/org/congcong/algomentor/api/problem/mapper/ProblemMapperXmlTest.java`
- Modify: `frontend/src/services/api.test.ts`
- Modify: `frontend/src/learning-plans/LearningPlanDraftPanel.test.tsx`
- Create: `frontend/src/learning-plans/LearningPlanExtensionPanel.test.tsx`

---

### Task 1: Proposal Domain Models and Repository Ports

**Files:**
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/proposal/LearningPlanProposalType.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/proposal/LearningPlanProposalTargetType.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/proposal/LearningPlanProposalGroupStatus.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/proposal/LearningPlanProposalRevisionStatus.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/proposal/LearningPlanProposalGroup.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/proposal/LearningPlanDraftRevision.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/proposal/LearningPlanExtensionRevision.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/proposal/LearningPlanExtensionDraft.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/proposal/LearningPlanProposalRepository.java`
- Modify: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/LearningPlanRepository.java`
- Modify: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/practice/PracticeSessionRepository.java`
- Test: `backend/mentor-application/src/test/java/org/congcong/algomentor/mentor/application/learningplan/proposal/LearningPlanProposalGroupServiceTest.java`

- [ ] **Step 1: Write failing domain smoke test**

Create `backend/mentor-application/src/test/java/org/congcong/algomentor/mentor/application/learningplan/proposal/LearningPlanProposalGroupServiceTest.java` with this first test:

```java
package org.congcong.algomentor.mentor.application.learningplan.proposal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LearningPlanProposalGroupServiceTest {

  private final Clock clock = Clock.fixed(Instant.parse("2026-07-01T00:00:00Z"), ZoneOffset.UTC);
  private final InMemoryProposalRepository repository = new InMemoryProposalRepository();
  private final LearningPlanProposalGroupService service = new LearningPlanProposalGroupService(repository, clock);

  @Test
  void createsDraftRevisionGroupAsActive() {
    LearningPlanProposalGroup group = service.createGroup(
        42L,
        LearningPlanProposalType.DRAFT_REVISION,
        LearningPlanProposalTargetType.DRAFT,
        100L,
        "减少动态规划题");

    assertThat(group.id()).isPositive();
    assertThat(group.userId()).isEqualTo(42L);
    assertThat(group.proposalType()).isEqualTo(LearningPlanProposalType.DRAFT_REVISION);
    assertThat(group.targetType()).isEqualTo(LearningPlanProposalTargetType.DRAFT);
    assertThat(group.targetId()).isEqualTo(100L);
    assertThat(group.status()).isEqualTo(LearningPlanProposalGroupStatus.ACTIVE);
    assertThat(group.initialInstruction()).isEqualTo("减少动态规划题");
    assertThat(group.latestProposalId()).isNull();
  }

  private static final class InMemoryProposalRepository implements LearningPlanProposalRepository {
    private final Map<Long, LearningPlanProposalGroup> groups = new HashMap<>();
    private final Map<Long, LearningPlanDraftRevision> draftRevisions = new HashMap<>();
    private final Map<Long, LearningPlanExtensionRevision> extensionRevisions = new HashMap<>();
    private long groupSequence = 10;
    private long proposalSequence = 100;

    @Override
    public LearningPlanProposalGroup saveGroup(LearningPlanProposalGroup group) {
      long id = group.id() == null ? groupSequence++ : group.id();
      LearningPlanProposalGroup saved = group.withId(id);
      groups.put(id, saved);
      return saved;
    }

    @Override
    public Optional<LearningPlanProposalGroup> findGroupForUser(long groupId, long userId) {
      return Optional.ofNullable(groups.get(groupId)).filter(group -> group.userId() == userId);
    }

    @Override
    public Optional<LearningPlanProposalGroup> findLatestActiveGroup(
        long userId,
        LearningPlanProposalType proposalType,
        LearningPlanProposalTargetType targetType,
        long targetId) {
      return groups.values().stream()
          .filter(group -> group.userId() == userId)
          .filter(group -> group.proposalType() == proposalType)
          .filter(group -> group.targetType() == targetType)
          .filter(group -> group.targetId() == targetId)
          .filter(group -> group.status() == LearningPlanProposalGroupStatus.ACTIVE)
          .max(Comparator.comparing(LearningPlanProposalGroup::createdAt));
    }

    @Override
    public LearningPlanDraftRevision saveDraftRevision(LearningPlanDraftRevision revision) {
      long id = revision.id() == null ? proposalSequence++ : revision.id();
      LearningPlanDraftRevision saved = revision.withId(id);
      draftRevisions.put(id, saved);
      return saved;
    }

    @Override
    public LearningPlanExtensionRevision saveExtensionRevision(LearningPlanExtensionRevision revision) {
      long id = revision.id() == null ? proposalSequence++ : revision.id();
      LearningPlanExtensionRevision saved = revision.withId(id);
      extensionRevisions.put(id, saved);
      return saved;
    }

    @Override
    public Optional<LearningPlanDraftRevision> findDraftRevisionForUser(long revisionId, long userId) {
      return Optional.ofNullable(draftRevisions.get(revisionId)).filter(revision -> revision.userId() == userId);
    }

    @Override
    public Optional<LearningPlanExtensionRevision> findExtensionRevisionForUser(long revisionId, long userId) {
      return Optional.ofNullable(extensionRevisions.get(revisionId)).filter(revision -> revision.userId() == userId);
    }

    @Override
    public Optional<LearningPlanExtensionRevision> findLatestReadyExtensionRevision(long proposalGroupId) {
      return extensionRevisions.values().stream()
          .filter(revision -> revision.proposalGroupId() == proposalGroupId)
          .filter(revision -> revision.status() == LearningPlanProposalRevisionStatus.READY)
          .max(Comparator.comparingInt(LearningPlanExtensionRevision::revisionNo));
    }

    @Override
    public int nextRevisionNo(long proposalGroupId) {
      long draftCount = draftRevisions.values().stream()
          .filter(revision -> revision.proposalGroupId() == proposalGroupId)
          .count();
      long extensionCount = extensionRevisions.values().stream()
          .filter(revision -> revision.proposalGroupId() == proposalGroupId)
          .count();
      return (int) Math.max(draftCount, extensionCount) + 1;
    }

    @Override
    public List<Long> markReadyDraftRevisionsSuperseded(long proposalGroupId, long exceptRevisionId) {
      List<Long> superseded = new ArrayList<>();
      draftRevisions.replaceAll((id, revision) -> {
        if (revision.proposalGroupId() == proposalGroupId
            && revision.id() != exceptRevisionId
            && revision.status() == LearningPlanProposalRevisionStatus.READY) {
          superseded.add(revision.id());
          return revision.withStatus(LearningPlanProposalRevisionStatus.SUPERSEDED, revision.updatedAt());
        }
        return revision;
      });
      return superseded;
    }

    @Override
    public List<Long> markReadyExtensionRevisionsSuperseded(long proposalGroupId, long exceptRevisionId) {
      List<Long> superseded = new ArrayList<>();
      extensionRevisions.replaceAll((id, revision) -> {
        if (revision.proposalGroupId() == proposalGroupId
            && revision.id() != exceptRevisionId
            && revision.status() == LearningPlanProposalRevisionStatus.READY) {
          superseded.add(revision.id());
          return revision.withStatus(LearningPlanProposalRevisionStatus.SUPERSEDED, revision.updatedAt());
        }
        return revision;
      });
      return superseded;
    }
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-application -am test -Dtest=LearningPlanProposalGroupServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: compilation fails because proposal classes do not exist.

- [ ] **Step 3: Add enums**

Create these enum files:

```java
package org.congcong.algomentor.mentor.application.learningplan.proposal;

public enum LearningPlanProposalType {
  DRAFT_REVISION,
  PLAN_EXTENSION
}
```

```java
package org.congcong.algomentor.mentor.application.learningplan.proposal;

public enum LearningPlanProposalTargetType {
  DRAFT,
  PLAN
}
```

```java
package org.congcong.algomentor.mentor.application.learningplan.proposal;

public enum LearningPlanProposalGroupStatus {
  ACTIVE,
  APPLIED,
  DISCARDED,
  EXPIRED
}
```

```java
package org.congcong.algomentor.mentor.application.learningplan.proposal;

public enum LearningPlanProposalRevisionStatus {
  GENERATING,
  READY,
  SUPERSEDED,
  FAILED,
  APPLIED,
  DISCARDED,
  EXPIRED
}
```

- [ ] **Step 4: Add proposal records**

Create `LearningPlanProposalGroup.java`:

```java
package org.congcong.algomentor.mentor.application.learningplan.proposal;

import java.time.Instant;

public record LearningPlanProposalGroup(
    Long id,
    long userId,
    LearningPlanProposalType proposalType,
    LearningPlanProposalTargetType targetType,
    long targetId,
    LearningPlanProposalGroupStatus status,
    String initialInstruction,
    Long latestProposalId,
    Instant createdAt,
    Instant updatedAt
) {

  public LearningPlanProposalGroup {
    if (userId < 1) {
      throw new IllegalArgumentException("Learning plan proposal user id must be positive");
    }
    if (proposalType == null) {
      throw new IllegalArgumentException("Learning plan proposal type must not be null");
    }
    if (targetType == null) {
      throw new IllegalArgumentException("Learning plan proposal target type must not be null");
    }
    if (targetId < 1) {
      throw new IllegalArgumentException("Learning plan proposal target id must be positive");
    }
    if (status == null) {
      throw new IllegalArgumentException("Learning plan proposal group status must not be null");
    }
    if (initialInstruction == null || initialInstruction.isBlank()) {
      throw new IllegalArgumentException("Learning plan proposal instruction must not be blank");
    }
    if (createdAt == null || updatedAt == null) {
      throw new IllegalArgumentException("Learning plan proposal timestamps must not be null");
    }
    initialInstruction = initialInstruction.trim();
  }

  public LearningPlanProposalGroup withId(Long nextId) {
    return new LearningPlanProposalGroup(
        nextId, userId, proposalType, targetType, targetId, status,
        initialInstruction, latestProposalId, createdAt, updatedAt);
  }

  public LearningPlanProposalGroup withLatestProposalId(Long proposalId, Instant now) {
    return new LearningPlanProposalGroup(
        id, userId, proposalType, targetType, targetId, status,
        initialInstruction, proposalId, createdAt, now);
  }

  public LearningPlanProposalGroup withStatus(LearningPlanProposalGroupStatus nextStatus, Instant now) {
    return new LearningPlanProposalGroup(
        id, userId, proposalType, targetType, targetId, nextStatus,
        initialInstruction, latestProposalId, createdAt, now);
  }
}
```

Create `LearningPlanExtensionDraft.java`:

```java
package org.congcong.algomentor.mentor.application.learningplan.proposal;

import java.util.List;
import java.util.Map;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanPhaseDraft;

public record LearningPlanExtensionDraft(
    String summary,
    List<LearningPlanPhaseDraft> newPhases,
    Map<String, Object> metadata
) {

  public LearningPlanExtensionDraft {
    summary = summary == null ? "" : summary.trim();
    newPhases = newPhases == null ? List.of() : List.copyOf(newPhases);
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }
}
```

Create `LearningPlanDraftRevision.java`:

```java
package org.congcong.algomentor.mentor.application.learningplan.proposal;

import java.time.Instant;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftPlan;

public record LearningPlanDraftRevision(
    Long id,
    long proposalGroupId,
    long draftId,
    long userId,
    int revisionNo,
    LearningPlanProposalRevisionStatus status,
    String instruction,
    LearningPlanDraftPlan basePlan,
    LearningPlanDraftPlan proposedPlan,
    String errorCode,
    String errorMessage,
    Instant createdAt,
    Instant updatedAt
) {

  public LearningPlanDraftRevision {
    if (proposalGroupId < 1 || draftId < 1 || userId < 1 || revisionNo < 1) {
      throw new IllegalArgumentException("Learning plan draft revision identifiers must be positive");
    }
    if (status == null) {
      throw new IllegalArgumentException("Learning plan draft revision status must not be null");
    }
    if (instruction == null || instruction.isBlank()) {
      throw new IllegalArgumentException("Learning plan draft revision instruction must not be blank");
    }
    if (createdAt == null || updatedAt == null) {
      throw new IllegalArgumentException("Learning plan draft revision timestamps must not be null");
    }
    instruction = instruction.trim();
  }

  public LearningPlanDraftRevision withId(Long nextId) {
    return new LearningPlanDraftRevision(
        nextId, proposalGroupId, draftId, userId, revisionNo, status, instruction,
        basePlan, proposedPlan, errorCode, errorMessage, createdAt, updatedAt);
  }

  public LearningPlanDraftRevision withStatus(LearningPlanProposalRevisionStatus nextStatus, Instant now) {
    return new LearningPlanDraftRevision(
        id, proposalGroupId, draftId, userId, revisionNo, nextStatus, instruction,
        basePlan, proposedPlan, errorCode, errorMessage, createdAt, now);
  }

  public LearningPlanDraftRevision withReady(LearningPlanDraftPlan nextPlan, Instant now) {
    return new LearningPlanDraftRevision(
        id, proposalGroupId, draftId, userId, revisionNo, LearningPlanProposalRevisionStatus.READY,
        instruction, basePlan, nextPlan, null, null, createdAt, now);
  }

  public LearningPlanDraftRevision withFailure(String code, String message, Instant now) {
    return new LearningPlanDraftRevision(
        id, proposalGroupId, draftId, userId, revisionNo, LearningPlanProposalRevisionStatus.FAILED,
        instruction, basePlan, proposedPlan, code, message, createdAt, now);
  }
}
```

Create `LearningPlanExtensionRevision.java`:

```java
package org.congcong.algomentor.mentor.application.learningplan.proposal;

import java.time.Instant;
import java.util.Map;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftPlan;

public record LearningPlanExtensionRevision(
    Long id,
    long proposalGroupId,
    long planId,
    long userId,
    int revisionNo,
    LearningPlanProposalRevisionStatus status,
    String instruction,
    LearningPlanDraftPlan basePlan,
    Map<String, Object> progressSnapshot,
    int baseMaxPhaseIndex,
    LearningPlanExtensionDraft previousExtension,
    LearningPlanExtensionDraft proposedExtension,
    Instant appliedAt,
    String errorCode,
    String errorMessage,
    Instant createdAt,
    Instant updatedAt
) {

  public LearningPlanExtensionRevision {
    if (proposalGroupId < 1 || planId < 1 || userId < 1 || revisionNo < 1) {
      throw new IllegalArgumentException("Learning plan extension revision identifiers must be positive");
    }
    if (status == null) {
      throw new IllegalArgumentException("Learning plan extension revision status must not be null");
    }
    if (instruction == null || instruction.isBlank()) {
      throw new IllegalArgumentException("Learning plan extension revision instruction must not be blank");
    }
    if (basePlan == null) {
      throw new IllegalArgumentException("Learning plan extension revision base plan must not be null");
    }
    if (createdAt == null || updatedAt == null) {
      throw new IllegalArgumentException("Learning plan extension revision timestamps must not be null");
    }
    instruction = instruction.trim();
    progressSnapshot = progressSnapshot == null ? Map.of() : Map.copyOf(progressSnapshot);
  }

  public LearningPlanExtensionRevision withId(Long nextId) {
    return new LearningPlanExtensionRevision(
        nextId, proposalGroupId, planId, userId, revisionNo, status, instruction, basePlan,
        progressSnapshot, baseMaxPhaseIndex, previousExtension, proposedExtension,
        appliedAt, errorCode, errorMessage, createdAt, updatedAt);
  }

  public LearningPlanExtensionRevision withStatus(LearningPlanProposalRevisionStatus nextStatus, Instant now) {
    return new LearningPlanExtensionRevision(
        id, proposalGroupId, planId, userId, revisionNo, nextStatus, instruction, basePlan,
        progressSnapshot, baseMaxPhaseIndex, previousExtension, proposedExtension,
        appliedAt, errorCode, errorMessage, createdAt, now);
  }

  public LearningPlanExtensionRevision withReady(LearningPlanExtensionDraft nextExtension, Instant now) {
    return new LearningPlanExtensionRevision(
        id, proposalGroupId, planId, userId, revisionNo, LearningPlanProposalRevisionStatus.READY,
        instruction, basePlan, progressSnapshot, baseMaxPhaseIndex, previousExtension, nextExtension,
        appliedAt, null, null, createdAt, now);
  }

  public LearningPlanExtensionRevision withApplied(Instant now) {
    return new LearningPlanExtensionRevision(
        id, proposalGroupId, planId, userId, revisionNo, LearningPlanProposalRevisionStatus.APPLIED,
        instruction, basePlan, progressSnapshot, baseMaxPhaseIndex, previousExtension, proposedExtension,
        now, null, null, createdAt, now);
  }

  public LearningPlanExtensionRevision withFailure(String code, String message, Instant now) {
    return new LearningPlanExtensionRevision(
        id, proposalGroupId, planId, userId, revisionNo, LearningPlanProposalRevisionStatus.FAILED,
        instruction, basePlan, progressSnapshot, baseMaxPhaseIndex, previousExtension, proposedExtension,
        appliedAt, code, message, createdAt, now);
  }
}
```

- [ ] **Step 5: Add repository ports**

Create `LearningPlanProposalRepository.java`:

```java
package org.congcong.algomentor.mentor.application.learningplan.proposal;

import java.util.List;
import java.util.Optional;

public interface LearningPlanProposalRepository {

  LearningPlanProposalGroup saveGroup(LearningPlanProposalGroup group);

  Optional<LearningPlanProposalGroup> findGroupForUser(long groupId, long userId);

  Optional<LearningPlanProposalGroup> findLatestActiveGroup(
      long userId,
      LearningPlanProposalType proposalType,
      LearningPlanProposalTargetType targetType,
      long targetId);

  LearningPlanDraftRevision saveDraftRevision(LearningPlanDraftRevision revision);

  LearningPlanExtensionRevision saveExtensionRevision(LearningPlanExtensionRevision revision);

  Optional<LearningPlanDraftRevision> findDraftRevisionForUser(long revisionId, long userId);

  Optional<LearningPlanExtensionRevision> findExtensionRevisionForUser(long revisionId, long userId);

  Optional<LearningPlanExtensionRevision> findLatestReadyExtensionRevision(long proposalGroupId);

  int nextRevisionNo(long proposalGroupId);

  List<Long> markReadyDraftRevisionsSuperseded(long proposalGroupId, long exceptRevisionId);

  List<Long> markReadyExtensionRevisionsSuperseded(long proposalGroupId, long exceptRevisionId);
}
```

Modify `LearningPlanRepository.java`:

```java
default LearningPlan appendPhases(long userId, long planId, List<LearningPlanPhaseDraft> newPhases) {
  throw new LearningPlanRepositoryUnavailableException();
}
```

Modify `PracticeSessionRepository.java`:

```java
java.util.List<PracticeProgress> findProgressByPlan(long userId, long planId);
```

- [ ] **Step 6: Add group service**

Create `LearningPlanProposalGroupService.java`:

```java
package org.congcong.algomentor.mentor.application.learningplan.proposal;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public class LearningPlanProposalGroupService {

  private final LearningPlanProposalRepository repository;
  private final Clock clock;

  public LearningPlanProposalGroupService(LearningPlanProposalRepository repository, Clock clock) {
    this.repository = Objects.requireNonNull(repository, "repository must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  public LearningPlanProposalGroup createGroup(
      long userId,
      LearningPlanProposalType proposalType,
      LearningPlanProposalTargetType targetType,
      long targetId,
      String instruction) {
    Instant now = clock.instant();
    return repository.saveGroup(new LearningPlanProposalGroup(
        null,
        userId,
        proposalType,
        targetType,
        targetId,
        LearningPlanProposalGroupStatus.ACTIVE,
        instruction,
        null,
        now,
        now));
  }
}
```

- [ ] **Step 7: Run application test**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-application -am test -Dtest=LearningPlanProposalGroupServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: PASS.

### Task 2: Proposal Persistence and Append-Only Plan Repository

**Files:**
- Create: `backend/mentor-api/src/main/resources/db/migration/V17__learning_plan_proposal_schema.sql`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/learningplan/mapper/model/LearningPlanProposalGroupRow.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/learningplan/mapper/model/LearningPlanDraftRevisionRow.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/learningplan/mapper/model/LearningPlanExtensionRevisionRow.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/learningplan/repository/MyBatisLearningPlanProposalRepository.java`
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/learningplan/mapper/LearningPlanMapper.java`
- Modify: `backend/mentor-api/src/main/resources/mapper/learningplan/LearningPlanMapper.xml`
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/learningplan/repository/MyBatisLearningPlanRepository.java`
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/practice/mapper/PracticeSessionMapper.java`
- Modify: `backend/mentor-api/src/main/resources/mapper/practice/PracticeSessionMapper.xml`
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/practice/repository/MyBatisPracticeSessionRepository.java`
- Test: `backend/mentor-api/src/test/java/org/congcong/algomentor/api/config/FlywayMigrationResourceTest.java`
- Test: `backend/mentor-api/src/test/java/org/congcong/algomentor/api/problem/mapper/ProblemMapperXmlTest.java`

- [ ] **Step 1: Write migration**

Before adding the file, confirm no newer migration exists:

```bash
find backend -path '*/db/migration*' -type f -name 'V*.sql' | sort
```

Expected: latest source migration is `V16__identity_user_soft_delete.sql`, so create `backend/mentor-api/src/main/resources/db/migration/V17__learning_plan_proposal_schema.sql`:

```sql
CREATE TABLE IF NOT EXISTS learning_plan_proposal_group (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  proposal_type VARCHAR(40) NOT NULL,
  target_type VARCHAR(40) NOT NULL,
  target_id BIGINT NOT NULL,
  status VARCHAR(40) NOT NULL,
  initial_instruction TEXT NOT NULL,
  latest_proposal_id BIGINT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_learning_plan_proposal_group_target
  ON learning_plan_proposal_group(user_id, proposal_type, target_type, target_id, status);

CREATE TABLE IF NOT EXISTS learning_plan_draft_revision (
  id BIGSERIAL PRIMARY KEY,
  proposal_group_id BIGINT NOT NULL REFERENCES learning_plan_proposal_group(id) ON DELETE CASCADE,
  draft_id BIGINT NOT NULL REFERENCES learning_plan_draft(id) ON DELETE CASCADE,
  user_id BIGINT NOT NULL,
  revision_no INTEGER NOT NULL,
  status VARCHAR(40) NOT NULL,
  instruction TEXT NOT NULL,
  base_plan_json JSONB,
  proposed_plan_json JSONB,
  error_code VARCHAR(120),
  error_message TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uk_learning_plan_draft_revision_no UNIQUE (proposal_group_id, revision_no)
);

CREATE INDEX IF NOT EXISTS idx_learning_plan_draft_revision_draft
  ON learning_plan_draft_revision(user_id, draft_id, status, created_at DESC);

CREATE TABLE IF NOT EXISTS learning_plan_extension_revision (
  id BIGSERIAL PRIMARY KEY,
  proposal_group_id BIGINT NOT NULL REFERENCES learning_plan_proposal_group(id) ON DELETE CASCADE,
  plan_id BIGINT NOT NULL REFERENCES learning_plan(id) ON DELETE CASCADE,
  user_id BIGINT NOT NULL,
  revision_no INTEGER NOT NULL,
  status VARCHAR(40) NOT NULL,
  instruction TEXT NOT NULL,
  base_plan_json JSONB NOT NULL,
  progress_snapshot_json JSONB NOT NULL DEFAULT '{}'::JSONB,
  base_max_phase_index INTEGER NOT NULL,
  previous_extension_json JSONB,
  proposed_extension_json JSONB,
  applied_at TIMESTAMPTZ,
  error_code VARCHAR(120),
  error_message TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uk_learning_plan_extension_revision_no UNIQUE (proposal_group_id, revision_no)
);

CREATE INDEX IF NOT EXISTS idx_learning_plan_extension_revision_plan
  ON learning_plan_extension_revision(user_id, plan_id, status, created_at DESC);
```

- [ ] **Step 2: Run migration resource test**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-api -am test -Dtest=FlywayMigrationResourceTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: PASS after the migration file uses the next available version and valid SQL resource naming.

- [ ] **Step 3: Add row records**

Create row records with the same package style as `LearningPlanRow`:

```java
package org.congcong.algomentor.api.learningplan.mapper.model;

import java.time.Instant;

public record LearningPlanProposalGroupRow(
    Long id,
    Long userId,
    String proposalType,
    String targetType,
    Long targetId,
    String status,
    String initialInstruction,
    Long latestProposalId,
    Instant createdAt,
    Instant updatedAt
) {
}
```

Create `LearningPlanDraftRevisionRow.java`:

```java
package org.congcong.algomentor.api.learningplan.mapper.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record LearningPlanDraftRevisionRow(
    Long id,
    Long proposalGroupId,
    Long draftId,
    Long userId,
    Integer revisionNo,
    String status,
    String instruction,
    JsonNode basePlanJson,
    JsonNode proposedPlanJson,
    String errorCode,
    String errorMessage,
    Instant createdAt,
    Instant updatedAt
) {
}
```

Create `LearningPlanExtensionRevisionRow.java`:

```java
package org.congcong.algomentor.api.learningplan.mapper.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record LearningPlanExtensionRevisionRow(
    Long id,
    Long proposalGroupId,
    Long planId,
    Long userId,
    Integer revisionNo,
    String status,
    String instruction,
    JsonNode basePlanJson,
    JsonNode progressSnapshotJson,
    Integer baseMaxPhaseIndex,
    JsonNode previousExtensionJson,
    JsonNode proposedExtensionJson,
    Instant appliedAt,
    String errorCode,
    String errorMessage,
    Instant createdAt,
    Instant updatedAt
) {
}
```

- [ ] **Step 4: Extend learning plan mapper interface**

Add proposal methods and append-only methods to `LearningPlanMapper.java`:

```java
long insertProposalGroup(LearningPlanProposalGroupRow row);

int updateProposalGroup(LearningPlanProposalGroupRow row);

LearningPlanProposalGroupRow findProposalGroupForUser(@Param("id") long id, @Param("userId") long userId);

LearningPlanProposalGroupRow findLatestActiveProposalGroup(
    @Param("userId") long userId,
    @Param("proposalType") String proposalType,
    @Param("targetType") String targetType,
    @Param("targetId") long targetId);

long insertDraftRevision(LearningPlanDraftRevisionRow row);

int updateDraftRevision(LearningPlanDraftRevisionRow row);

LearningPlanDraftRevisionRow findDraftRevisionForUser(@Param("id") long id, @Param("userId") long userId);

long insertExtensionRevision(LearningPlanExtensionRevisionRow row);

int updateExtensionRevision(LearningPlanExtensionRevisionRow row);

LearningPlanExtensionRevisionRow findExtensionRevisionForUser(@Param("id") long id, @Param("userId") long userId);

LearningPlanExtensionRevisionRow findLatestReadyExtensionRevision(@Param("proposalGroupId") long proposalGroupId);

int nextDraftRevisionNo(@Param("proposalGroupId") long proposalGroupId);

int nextExtensionRevisionNo(@Param("proposalGroupId") long proposalGroupId);

java.util.List<Long> markReadyDraftRevisionsSuperseded(
    @Param("proposalGroupId") long proposalGroupId,
    @Param("exceptRevisionId") long exceptRevisionId,
    @Param("updatedAt") java.time.Instant updatedAt);

java.util.List<Long> markReadyExtensionRevisionsSuperseded(
    @Param("proposalGroupId") long proposalGroupId,
    @Param("exceptRevisionId") long exceptRevisionId,
    @Param("updatedAt") java.time.Instant updatedAt);

Integer findMaxPhaseIndex(@Param("planId") long planId);

int updatePlanJsonSnapshot(
    @Param("planId") long planId,
    @Param("userId") long userId,
    @Param("title") String title,
    @Param("planJson") com.fasterxml.jackson.databind.JsonNode planJson,
    @Param("updatedAt") java.time.Instant updatedAt);
```

- [ ] **Step 5: Extend MyBatis XML**

Add result maps and SQL statements to `LearningPlanMapper.xml`. Keep JSONB fields using `org.congcong.algomentor.agent.persistence.postgres.json.JsonbTypeHandler`. The append-only implementation must not reference `deletePlanPhases`.

Use this query shape for supersede:

```xml
<select id="markReadyExtensionRevisionsSuperseded" resultType="java.lang.Long" affectData="true">
  UPDATE learning_plan_extension_revision
  SET status = 'SUPERSEDED',
      updated_at = #{updatedAt}
  WHERE proposal_group_id = #{proposalGroupId}
    AND id != #{exceptRevisionId}
    AND status = 'READY'
  RETURNING id
</select>
```

Use this query shape for max phase:

```xml
<select id="findMaxPhaseIndex" resultType="java.lang.Integer">
  SELECT COALESCE(MAX(phase_index), 0)
  FROM learning_plan_phase
  WHERE plan_id = #{planId}
</select>
```

- [ ] **Step 6: Implement proposal repository**

Create `MyBatisLearningPlanProposalRepository.java` implementing `LearningPlanProposalRepository`. Convert JSON with `ObjectMapper.valueToTree(...)` and `treeToValue(...)`, following `MyBatisLearningPlanRepository`. `nextRevisionNo(...)` returns `Math.max(mapper.nextDraftRevisionNo(groupId), mapper.nextExtensionRevisionNo(groupId))`.

- [ ] **Step 7: Implement append-only plan repository method**

Modify `MyBatisLearningPlanRepository.appendPhases(...)`:

```java
@Override
@Transactional
public LearningPlan appendPhases(long userId, long planId, List<LearningPlanPhaseDraft> newPhases) {
  LearningPlan current = findPlanByIdForUser(planId, userId)
      .orElseThrow(() -> new LearningPlanException("LEARNING_PLAN_NOT_FOUND", "学习计划不存在。"));
  List<LearningPlanPhaseDraft> mergedPhases = new java.util.ArrayList<>(current.plan().phases());
  mergedPhases.addAll(newPhases);
  LearningPlanDraftPlan mergedPlan = new LearningPlanDraftPlan(
      current.plan().title(),
      current.plan().summary(),
      current.plan().intent(),
      current.plan().goal(),
      current.plan().durationWeeks(),
      current.plan().level(),
      current.plan().weeklyHours(),
      current.plan().programmingLanguage(),
      current.plan().difficultyPreference(),
      current.plan().interviewOriented(),
      current.plan().topicPreferences(),
      current.plan().profileSummary(),
      mergedPhases,
      current.plan().metadata());
  for (LearningPlanPhaseDraft phase : newPhases) {
    mapper.insertPlanPhase(planId, phase.phaseIndex(), phase.title(), phase.durationWeeks(), phase.focus());
    for (LearningPlanProblemDraft problem : phase.problems()) {
      mapper.insertPlanProblem(
          planId,
          phase.phaseIndex(),
          problem.slug(),
          problem.frontendId(),
          problem.title(),
          problem.titleCn(),
          problem.difficulty(),
          problem.reason(),
          problem.sortOrder());
    }
  }
  mapper.updatePlanJsonSnapshot(planId, userId, mergedPlan.title(), json(mergedPlan), java.time.Instant.now());
  return toPlan(mapper.findPlanByIdForUser(planId, userId));
}
```

Because the current repository constructor does not inject `Clock`, keep the timestamp local to this method and always return the plan row reloaded from the database after the update.

- [ ] **Step 8: Add progress list query**

Add `findProgressByPlan(...)` to `PracticeSessionMapper`, `PracticeSessionMapper.xml`, and `MyBatisPracticeSessionRepository`:

```xml
<select id="findProgressByPlan" resultMap="PracticeProgressRowMap">
  SELECT
    <include refid="PracticeProgressColumns"/>
  FROM learning_plan_problem_progress
  WHERE user_id = #{userId}
    AND plan_id = #{planId}
  ORDER BY phase_index ASC, problem_slug ASC
</select>
```

- [ ] **Step 9: Run mapper tests**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-api -am test -Dtest=ProblemMapperXmlTest,FlywayMigrationResourceTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: PASS.

### Task 3: Extension Validator and Apply Service

**Files:**
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/proposal/LearningPlanExtensionValidator.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/proposal/LearningPlanExtensionApplyService.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/proposal/LearningPlanExtensionApplyResult.java`
- Test: `backend/mentor-application/src/test/java/org/congcong/algomentor/mentor/application/learningplan/proposal/LearningPlanExtensionValidatorTest.java`
- Test: `backend/mentor-application/src/test/java/org/congcong/algomentor/mentor/application/learningplan/proposal/LearningPlanExtensionApplyServiceTest.java`

- [ ] **Step 1: Write failing validator tests**

Create tests covering these exact cases:

```java
@Test
void rejectsEmptyNewPhases() {
  LearningPlanExtensionDraft extension = new LearningPlanExtensionDraft("空扩展", List.of(), Map.of());

  assertThatThrownBy(() -> validator.validate(extension, currentPlan(), List.of()))
      .isInstanceOf(LearningPlanException.class)
      .hasMessage("扩展提案至少需要一个新增阶段。");
}

@Test
void rejectsExistingProblemSlug() {
  LearningPlanExtensionDraft extension = extensionWithProblem(3, "two-sum");

  assertThatThrownBy(() -> validator.validate(extension, currentPlan(), List.of()))
      .isInstanceOf(LearningPlanException.class)
      .hasMessage("扩展提案不能重复推荐已有计划题目。");
}

@Test
void rejectsUnknownProblemSlug() {
  LearningPlanExtensionDraft extension = extensionWithProblem(3, "unknown-problem");

  assertThatThrownBy(() -> validator.validate(extension, currentPlan(), List.of()))
      .isInstanceOf(LearningPlanException.class)
      .hasMessage("扩展提案包含本地题库不存在的题目。");
}
```

Use a fake `LearningPlanProblemCatalog` where `findBySlug("graph-valid-tree")` returns a candidate and `findBySlug("unknown-problem")` returns empty.

- [ ] **Step 2: Run validator tests to verify failure**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-application -am test -Dtest=LearningPlanExtensionValidatorTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: compilation fails because `LearningPlanExtensionValidator` does not exist.

- [ ] **Step 3: Implement validator**

Create `LearningPlanExtensionValidator.java`:

```java
package org.congcong.algomentor.mentor.application.learningplan.proposal;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanPhaseDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemCatalog;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemDraft;
import org.congcong.algomentor.mentor.application.practice.PracticeProgress;

public class LearningPlanExtensionValidator {

  private final LearningPlanProblemCatalog problemCatalog;

  public LearningPlanExtensionValidator(LearningPlanProblemCatalog problemCatalog) {
    this.problemCatalog = problemCatalog;
  }

  public void validate(LearningPlanExtensionDraft extension, LearningPlan currentPlan, List<PracticeProgress> progress) {
    if (extension == null || extension.newPhases().isEmpty()) {
      throw new LearningPlanException("LEARNING_PLAN_EXTENSION_INVALID", "扩展提案至少需要一个新增阶段。");
    }
    int currentMaxPhaseIndex = currentPlan.plan().phases().stream()
        .mapToInt(LearningPlanPhaseDraft::phaseIndex)
        .max()
        .orElse(0);
    Set<String> existingSlugs = new HashSet<>();
    for (LearningPlanPhaseDraft phase : currentPlan.plan().phases()) {
      for (LearningPlanProblemDraft problem : phase.problems()) {
        existingSlugs.add(problem.slug());
      }
    }
    Set<Integer> phaseIndexes = new HashSet<>();
    Set<String> extensionSlugs = new HashSet<>();
    int previousPhaseIndex = currentMaxPhaseIndex;
    for (LearningPlanPhaseDraft phase : extension.newPhases()) {
      if (phase.phaseIndex() <= currentMaxPhaseIndex || phase.phaseIndex() <= previousPhaseIndex) {
        throw new LearningPlanException("LEARNING_PLAN_EXTENSION_INVALID", "扩展阶段编号必须追加在当前计划之后并递增。");
      }
      if (!phaseIndexes.add(phase.phaseIndex())) {
        throw new LearningPlanException("LEARNING_PLAN_EXTENSION_INVALID", "扩展阶段编号不能重复。");
      }
      if (phase.problems().size() > 5) {
        throw new LearningPlanException("LEARNING_PLAN_EXTENSION_INVALID", "每个扩展阶段最多推荐 5 道题。");
      }
      for (LearningPlanProblemDraft problem : phase.problems()) {
        if (existingSlugs.contains(problem.slug())) {
          throw new LearningPlanException("LEARNING_PLAN_EXTENSION_INVALID", "扩展提案不能重复推荐已有计划题目。");
        }
        if (!extensionSlugs.add(problem.slug())) {
          throw new LearningPlanException("LEARNING_PLAN_EXTENSION_INVALID", "扩展提案内部不能重复推荐同一道题。");
        }
        if (problemCatalog.findBySlug(problem.slug()).isEmpty()) {
          throw new LearningPlanException("LEARNING_PLAN_EXTENSION_INVALID", "扩展提案包含本地题库不存在的题目。");
        }
      }
      previousPhaseIndex = phase.phaseIndex();
    }
  }
}
```

- [ ] **Step 4: Run validator tests**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-application -am test -Dtest=LearningPlanExtensionValidatorTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: PASS.

- [ ] **Step 5: Write failing apply service tests**

Create `LearningPlanExtensionApplyServiceTest.java` with these cases:

- latest `READY` revision applies and marks group/revision `APPLIED`;
- non-latest revision throws `LEARNING_PLAN_PROPOSAL_NOT_LATEST`;
- duplicate problem after another extension throws `LEARNING_PLAN_EXTENSION_CONFLICT`;
- max phase index drift renumbers new phases to current max + 1.

The in-memory `LearningPlanRepository.appendPhases(...)` must assert existing phases are still present after append.

- [ ] **Step 6: Implement apply service**

Create `LearningPlanExtensionApplyService.java` with method:

```java
public LearningPlanExtensionApplyResult apply(long userId, long planId, long proposalGroupId)
```

Behavior:

1. Read proposal group and require `ACTIVE`, `PLAN_EXTENSION`, `PLAN`.
2. Read latest ready extension revision for the group.
3. Require `revision.planId() == planId` and `revision.userId() == userId`.
4. Read current plan and progress.
5. Re-number extension phases if current max phase index differs from `baseMaxPhaseIndex`.
6. Run `LearningPlanExtensionValidator.validate(...)`.
7. Call `learningPlanRepository.appendPhases(...)`.
8. Save revision status `APPLIED` with `appliedAt`.
9. Save group status `APPLIED`.
10. Return `LearningPlanExtensionApplyResult` containing `planId`, `proposalGroupId`, `proposalId`, `status`, and appended phase count.

- [ ] **Step 7: Run apply service tests**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-application -am test -Dtest=LearningPlanExtensionApplyServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: PASS.

### Task 4: Draft Revision Streaming Service

**Files:**
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/proposal/LearningPlanDraftRevisionResult.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/proposal/stream/LearningPlanProposalEvent.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/proposal/stream/LearningPlanProposalStreamEvent.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/proposal/stream/LearningPlanDraftRevisionStreamService.java`
- Modify: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/stream/LearningPlanStreamConstants.java`
- Test: `backend/mentor-application/src/test/java/org/congcong/algomentor/mentor/application/learningplan/proposal/stream/LearningPlanDraftRevisionStreamServiceTest.java`

- [ ] **Step 1: Add event constants**

Modify `LearningPlanStreamConstants.java`:

```java
public static final String DRAFT_REVISION_READY = "draft_revision_ready";

public static final String DRAFT_REVISION_ERROR = "draft_revision_error";

public static final String PLAN_EXTENSION_READY = "plan_extension_ready";

public static final String PLAN_EXTENSION_ERROR = "plan_extension_error";

public static final String EXTENSION_SCHEMA_NAME = "learning_plan_extension";

public static final String EXTENSION_SCHEMA_VERSION = "v1";
```

- [ ] **Step 2: Write failing stream service test**

Create a test that uses a fake `AgentLoopRunner` publisher returning final JSON and asserts:

- generated revision status becomes `READY`;
- previous ready revision is superseded;
- `learning_plan_draft.draft_plan_json` is replaced;
- final emitted event name is `draft_revision_ready`.

Build a minimal `Flow.Publisher<AgentStreamEvent>` helper in the test that emits one `AgentStepStart`, one `Llm(ContentDelta(finalJson))`, one `AgentStepEnd` with `toolCallCount = 0`, and one `AgentRunEnd`.

- [ ] **Step 3: Implement proposal events**

Create `LearningPlanProposalEvent.java`:

```java
package org.congcong.algomentor.mentor.application.learningplan.proposal.stream;

import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanDraftRevisionResult;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanExtensionResult;

public sealed interface LearningPlanProposalEvent
    permits LearningPlanProposalEvent.DraftRevisionReady,
    LearningPlanProposalEvent.PlanExtensionReady,
    LearningPlanProposalEvent.ProposalError {

  record DraftRevisionReady(LearningPlanDraftRevisionResult result) implements LearningPlanProposalEvent {
  }

  record PlanExtensionReady(LearningPlanExtensionResult result) implements LearningPlanProposalEvent {
  }

  record ProposalError(String code, String message, boolean retryable) implements LearningPlanProposalEvent {
  }
}
```

Create `LearningPlanProposalStreamEvent.java` mirroring `LearningPlanDraftStreamEvent`, with `Work(AgentWorkStatusEvent)` and `Proposal(LearningPlanProposalEvent)` variants. `Proposal.eventName()` returns `draft_revision_ready`, `plan_extension_ready`, `draft_revision_error`, or `plan_extension_error` based on event kind and stream profile.

- [ ] **Step 4: Implement draft revision stream service**

Create `LearningPlanDraftRevisionStreamService.stream(long userId, long draftId, String instruction, String runId, Map<String, Object> metadata)`.

Implementation requirements:

- Fetch draft by `draftRepository.findDraftByIdForUser(...)`.
- Reject missing draft with `LEARNING_PLAN_DRAFT_NOT_FOUND`.
- Reject `CONFIRMED` draft with `LEARNING_PLAN_DRAFT_REVISION_NOT_ALLOWED`.
- Create or reuse latest active `DRAFT_REVISION` group for the draft.
- Insert `GENERATING` revision with `basePlanJson = draft.draftPlan()`.
- Build prompt from original command, current plan and instruction.
- Use provider-native schema `LearningPlanDraftJsonSchema.schema()`.
- On success, map with `LearningPlanDraftStructuredOutputMapper`, validate, supersede previous ready revisions, save revision as `READY`, update group `latestProposalId`, update draft state to `GENERATED`, append instruction to messages, emit `DraftRevisionReady`.
- On failure, save revision as `FAILED`, emit `ProposalError` with event name `draft_revision_error`.

- [ ] **Step 5: Run draft revision stream tests**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-application -am test -Dtest=LearningPlanDraftRevisionStreamServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: PASS.

### Task 5: Extension Proposal Streaming Service

**Files:**
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/proposal/LearningPlanExtensionResult.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/proposal/LearningPlanProposalPromptBuilder.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/proposal/stream/LearningPlanExtensionJsonSchema.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/proposal/stream/LearningPlanExtensionStructuredOutputMapper.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/proposal/stream/LearningPlanExtensionProposalStreamService.java`
- Test: `backend/mentor-application/src/test/java/org/congcong/algomentor/mentor/application/learningplan/proposal/stream/LearningPlanExtensionProposalStreamServiceTest.java`

- [ ] **Step 1: Write failing extension stream tests**

Cover:

- first extension generation creates a `PLAN_EXTENSION` group and a `READY` revision;
- revision generation for the same group stores previous extension JSON and supersedes old ready revision;
- structured output with duplicate slug stores `FAILED` and emits `plan_extension_error`.

- [ ] **Step 2: Add extension JSON schema**

Create `LearningPlanExtensionJsonSchema.schema()` with required top-level fields:

```json
{
  "type": "object",
  "additionalProperties": false,
  "required": ["summary", "newPhases", "metadata"],
  "properties": {
    "summary": { "type": "string" },
    "newPhases": {
      "type": "array",
      "minItems": 1,
      "items": {
        "type": "object",
        "additionalProperties": false,
        "required": ["phaseIndex", "title", "durationWeeks", "focus", "objectives", "recommendedTags", "acceptanceCriteria", "reviewAdvice", "problems"]
      }
    },
    "metadata": { "type": "object" }
  }
}
```

Add nested phase and problem schema helpers in `LearningPlanExtensionJsonSchema`, with these required phase fields: `phaseIndex`, `title`, `durationWeeks`, `focus`, `objectives`, `recommendedTags`, `acceptanceCriteria`, `reviewAdvice`, `problems`. Problem items require `slug`, `frontendId`, `title`, `titleCn`, `difficulty`, `tags`, `reason`, and `sortOrder`.

- [ ] **Step 3: Add structured output mapper**

Create `LearningPlanExtensionStructuredOutputMapper`:

```java
public LearningPlanExtensionDraft map(JsonNode node) {
  if (node == null || node.isNull()) {
    throw new LearningPlanException("LEARNING_PLAN_EXTENSION_STRUCTURED_OUTPUT_INVALID", "模型未返回扩展提案。");
  }
  try {
    return objectMapper.treeToValue(node, LearningPlanExtensionDraft.class);
  } catch (JsonProcessingException exception) {
    throw new LearningPlanException("LEARNING_PLAN_EXTENSION_STRUCTURED_OUTPUT_INVALID", "扩展提案结构化结果解析失败。");
  }
}
```

- [ ] **Step 4: Implement prompt builder**

Create `LearningPlanProposalPromptBuilder` with:

```java
public List<LlmMessage> buildDraftRevisionPrompt(
    String instruction,
    LearningPlanDraftCommand command,
    LearningPlanDraftPlan currentPlan)

public List<LlmMessage> buildExtensionPrompt(
    String instruction,
    LearningPlan currentPlan,
    List<PracticeProgress> progress)

public List<LlmMessage> buildExtensionRevisionPrompt(
    String instruction,
    LearningPlan currentPlan,
    List<PracticeProgress> progress,
    LearningPlanExtensionDraft previousExtension)
```

The extension system prompt must include these exact constraints in Chinese:

```text
只能追加新阶段，不能删除、修改、重排已有阶段。
新增题目不能和已有计划题目重复。
新增题目必须来自本地题库工具。
最终只输出扩展草案 JSON，不输出完整替换版计划。
```

- [ ] **Step 5: Implement extension stream service**

Create:

```java
public Flow.Publisher<LearningPlanProposalStreamEvent> streamFirstRevision(
    long userId,
    long planId,
    String instruction,
    String runId,
    Map<String, Object> metadata)

public Flow.Publisher<LearningPlanProposalStreamEvent> streamNextRevision(
    long userId,
    long planId,
    long proposalGroupId,
    String instruction,
    String runId,
    Map<String, Object> metadata)
```

Use `LearningPlanExtensionJsonSchema.schema()`, validate generated extension, supersede previous ready extension revisions, and emit `plan_extension_ready`.

- [ ] **Step 6: Run extension stream tests**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-application -am test -Dtest=LearningPlanExtensionProposalStreamServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: PASS.

### Task 6: API Endpoints, SSE Mapping, Governance, and Wiring

**Files:**
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/learningplan/model/LearningPlanRevisionRequest.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/learningplan/model/LearningPlanDraftRevisionReadyResponse.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/learningplan/model/LearningPlanExtensionReadyResponse.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/learningplan/model/LearningPlanExtensionApplyResponse.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/learningplan/service/LearningPlanProposalStreamSseMapper.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/learningplan/service/SseLearningPlanProposalStreamSubscriber.java`
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/config/ApiContractConstants.java`
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/learningplan/LearningPlanController.java`
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/learningplan/config/LearningPlanConfiguration.java`
- Modify: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/model/AiRunSource.java`
- Modify: `backend/ops-observability/src/main/java/org/congcong/algomentor/ops/observability/SseStreamType.java`
- Test: `backend/mentor-api/src/test/java/org/congcong/algomentor/api/controller/learningplan/LearningPlanControllerTest.java`

- [ ] **Step 1: Add request and response DTOs**

Create `LearningPlanRevisionRequest.java`:

```java
package org.congcong.algomentor.api.learningplan.model;

public record LearningPlanRevisionRequest(String instruction) {

  public String normalizedInstruction() {
    return instruction == null ? "" : instruction.trim();
  }
}
```

Create response records matching the design examples. `LearningPlanDraftRevisionReadyResponse` contains `proposalGroupId`, `proposalId`, `draftId`, `revisionNo`, `status`, `supersededProposalId`, and `LearningPlanDraftResponse draft`. `LearningPlanExtensionReadyResponse` contains `proposalGroupId`, `proposalId`, `planId`, `revisionNo`, `status`, `supersededProposalId`, `summary`, and `LearningPlanExtensionDraft extensionDraft`.

- [ ] **Step 2: Extend API constants**

Add constants:

```java
public static final String LEARNING_PLAN_DRAFT_REVISIONS_STREAM_PATH = "/{draftId}/revisions/stream";

public static final String LEARNING_PLAN_EXTENSION_PROPOSALS_STREAM_PATH = "/{planId}/extension-proposals/stream";

public static final String LEARNING_PLAN_EXTENSION_PROPOSAL_REVISIONS_STREAM_PATH =
    "/{planId}/extension-proposals/{proposalGroupId}/revisions/stream";

public static final String LEARNING_PLAN_EXTENSION_PROPOSAL_APPLY_PATH =
    "/{planId}/extension-proposals/{proposalGroupId}/apply";

public static final String LEARNING_PLAN_EXTENSION_PROPOSAL_DISCARD_PATH =
    "/{planId}/extension-proposals/{proposalGroupId}/discard";
```

- [ ] **Step 3: Add governance source and SSE stream type**

Add `LEARNING_PLAN_DRAFT_REVISION` and `LEARNING_PLAN_EXTENSION_PROPOSAL` to `AiRunSource`.

Add `LEARNING_PLAN_PROPOSAL("learning_plan_proposal")` to `SseStreamType`.

- [ ] **Step 4: Write failing controller tests**

Extend `LearningPlanControllerTest` with:

- `streamDraftRevisionReturnsSseAndUsesLearningPlanDraftRevisionSource`;
- `streamExtensionProposalReturnsSseAndUsesLearningPlanExtensionSource`;
- `applyExtensionProposalReturnsAppliedResponse`;
- `discardExtensionProposalUsesCurrentUser`.

Assert SSE content contains `event:draft_revision_ready` and `event:plan_extension_ready`.

- [ ] **Step 5: Implement SSE mapper and subscriber**

`LearningPlanProposalStreamSseMapper` maps work events like `LearningPlanDraftStreamSseMapper` and maps proposal events to DTOs. `SseLearningPlanProposalStreamSubscriber` can reuse the structure of `SseLearningPlanDraftStreamSubscriber`, but records `SseStreamType.LEARNING_PLAN_PROPOSAL`.

- [ ] **Step 6: Add controller methods**

In `LearningPlanController`, add:

```java
@PostMapping(value = ApiContractConstants.LEARNING_PLAN_DRAFTS_PATH
    + ApiContractConstants.LEARNING_PLAN_DRAFT_REVISIONS_STREAM_PATH,
    produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter streamDraftRevision(@PathVariable long draftId, @RequestBody LearningPlanRevisionRequest request)
```

```java
@PostMapping(value = "/{planId}/extension-proposals/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter streamExtensionProposal(@PathVariable long planId, @RequestBody LearningPlanRevisionRequest request)
```

```java
@PostMapping(value = "/{planId}/extension-proposals/{proposalGroupId}/revisions/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter streamExtensionProposalRevision(
    @PathVariable long planId,
    @PathVariable long proposalGroupId,
    @RequestBody LearningPlanRevisionRequest request)
```

```java
@PostMapping("/{planId}/extension-proposals/{proposalGroupId}/apply")
public ApiResponse<LearningPlanExtensionApplyResponse> applyExtensionProposal(
    @PathVariable long planId,
    @PathVariable long proposalGroupId)
```

Use the existing `streamDraft(...)` governance pattern. For draft revision, use `AiRunSource.LEARNING_PLAN_DRAFT_REVISION`; for extension, use `AiRunSource.LEARNING_PLAN_EXTENSION_PROPOSAL`.

- [ ] **Step 7: Wire beans**

Modify `LearningPlanConfiguration` to register:

- `LearningPlanProposalGroupService`
- `LearningPlanExtensionValidator`
- `LearningPlanDraftRevisionStreamService`
- `LearningPlanExtensionProposalStreamService`
- `LearningPlanExtensionApplyService`
- `LearningPlanProposalPromptBuilder`

- [ ] **Step 8: Run controller tests**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-api -am test -Dtest=LearningPlanControllerTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: PASS.

### Task 7: Frontend API Types and Services

**Files:**
- Modify: `frontend/src/types/api.ts`
- Modify: `frontend/src/services/api.ts`
- Test: `frontend/src/services/api.test.ts`

- [ ] **Step 1: Add TypeScript types**

Modify `frontend/src/types/api.ts`:

```ts
export type LearningPlanProposalRevisionStatus =
  | 'GENERATING'
  | 'READY'
  | 'SUPERSEDED'
  | 'FAILED'
  | 'APPLIED'
  | 'DISCARDED'
  | 'EXPIRED';

export interface LearningPlanRevisionRequest {
  instruction: string;
}

export interface LearningPlanExtensionDraft {
  summary: string;
  newPhases: LearningPlanPhaseDraft[];
  metadata: Record<string, unknown>;
}

export interface LearningPlanDraftRevisionReadyEvent {
  proposalGroupId: number;
  proposalId: number;
  draftId: number;
  revisionNo: number;
  status: LearningPlanProposalRevisionStatus;
  supersededProposalId?: number | null;
  draft: LearningPlanDraftResponse;
}

export interface LearningPlanExtensionReadyEvent {
  proposalGroupId: number;
  proposalId: number;
  planId: number;
  revisionNo: number;
  status: LearningPlanProposalRevisionStatus;
  supersededProposalId?: number | null;
  summary: string;
  extensionDraft: LearningPlanExtensionDraft;
}

export interface LearningPlanExtensionApplyResponse {
  planId: number;
  proposalGroupId: number;
  proposalId: number;
  status: 'APPLIED';
  appendedPhaseCount: number;
}
```

Add SSE event names:

```ts
| 'draft_revision_ready'
| 'draft_revision_error'
| 'plan_extension_ready'
| 'plan_extension_error'
```

- [ ] **Step 2: Add service functions**

Modify `frontend/src/services/api.ts`:

```ts
export async function streamLearningPlanDraftRevision(
  draftId: number,
  request: LearningPlanRevisionRequest,
  options: StreamLearningPlanDraftOptions,
): Promise<void> {
  const response = await apiFetch(`/api/learning-plans/drafts/${draftId}/revisions/stream`, {
    method: 'POST',
    headers: {
      Accept: 'text/event-stream, application/json',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
    signal: options.signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Learning plan draft revision stream failed');
  }
  if (!response.body) {
    throw new Error('Learning plan draft revision stream response does not include a readable body');
  }

  options.onOpen?.();
  await readEventStream(response.body, options.onEvent);
}
```

Add `streamLearningPlanExtensionProposal(...)`, `streamLearningPlanExtensionProposalRevision(...)`, `applyLearningPlanExtensionProposal(...)`, and `discardLearningPlanExtensionProposal(...)` using the exact endpoint paths from Task 6.

- [ ] **Step 3: Add service tests**

In `frontend/src/services/api.test.ts`, assert that:

- `streamLearningPlanDraftRevision(100, { instruction: '降低难度' }, ...)` posts to `/api/learning-plans/drafts/100/revisions/stream`;
- `streamLearningPlanExtensionProposal(88, ...)` posts to `/api/learning-plans/88/extension-proposals/stream`;
- `applyLearningPlanExtensionProposal(88, 30)` posts to `/api/learning-plans/88/extension-proposals/30/apply`.

- [ ] **Step 4: Run API service tests**

Run:

```bash
npm --cache ./.npm --prefix frontend test -- api.test.ts
```

Expected: PASS.

### Task 8: Frontend Draft Revision UX

**Files:**
- Modify: `frontend/src/learning-plans/LearningPlanCreatePage.tsx`
- Modify: `frontend/src/learning-plans/LearningPlanDraftPanel.tsx`
- Modify: `frontend/src/i18n/locales.ts`
- Modify: `frontend/src/styles.css`
- Test: `frontend/src/learning-plans/LearningPlanDraftPanel.test.tsx`

- [ ] **Step 1: Update component props**

Change `LearningPlanDraftPanelProps`:

```ts
interface LearningPlanDraftPanelProps {
  draft: LearningPlanDraftResponse;
  loading: boolean;
  workEvent?: AgentWorkStatusEvent;
  onConfirm: () => void;
  onRetryCreate?: () => void;
  onReturnToWizard?: () => void;
  onSendFollowUp: (message: string) => Promise<boolean>;
  onReviseDraft: (instruction: string) => Promise<boolean>;
}
```

Remove `onRegenerateGoal` and the `editingGoal` state.

- [ ] **Step 2: Add revision UI**

Inside `draft.status === 'GENERATED'` render a textarea and button after `PlanPreview` and before confirm:

```tsx
<label className="topic-field" htmlFor={revisionInputId}>
  <span>{resources.learningPlans.revisionInstructionLabel}</span>
  <textarea
    disabled={loading}
    id={revisionInputId}
    onChange={(event) => setRevisionInstruction(event.target.value)}
    rows={3}
    value={revisionInstruction}
  />
</label>
<div className="button-row">
  <button
    className="secondary-button"
    disabled={loading || !revisionInstruction.trim()}
    onClick={() => {
      void onReviseDraft(revisionInstruction.trim()).then((sent) => {
        if (sent) {
          setRevisionInstruction('');
        }
      });
    }}
    type="button"
  >
    <Send aria-hidden="true" />
    <span>{resources.learningPlans.reviseDraft}</span>
  </button>
  <button className="primary-button" disabled={loading} onClick={onConfirm} type="button">
    <Check aria-hidden="true" />
    <span>{resources.learningPlans.savePlan}</span>
  </button>
</div>
```

- [ ] **Step 3: Stream revisions in create page**

In `LearningPlanCreatePage.tsx`, import `streamLearningPlanDraftRevision`, add `reviseDraft(instruction: string)`, and handle these events:

```ts
if (event.eventName === 'draft_revision_ready') {
  const revision = event.data as LearningPlanDraftRevisionReadyEvent;
  setDraft(revision.draft);
  setFlowState('previewing');
  return;
}
if (event.eventName === 'draft_revision_error') {
  const draftError = event.data as LearningPlanDraftErrorEvent;
  setError(draftError.message || resources.learningPlans.followUpFailed);
  setFlowState('previewing');
}
```

Remove `regenerateFromGoal(...)` and do not call `resources.learningPlans.followUpRegeneratePrefix(...)`.

- [ ] **Step 4: Update locale copy**

Add Chinese and English keys:

```ts
revisionInstructionLabel: string;
reviseDraft: string;
revisionFailed: string;
```

Use Chinese values:

```ts
revisionInstructionLabel: '对当前计划不满意？输入调整要求',
reviseDraft: '按要求调整计划',
revisionFailed: '调整学习计划失败，请稍后重试。',
```

- [ ] **Step 5: Update draft panel tests**

Replace tests for “编辑目标摘要” with:

- generated draft displays revision textarea;
- clicking `按要求调整计划` calls `onReviseDraft` with trimmed text;
- successful revision clears textarea;
- confirm button remains available.

- [ ] **Step 6: Run draft panel tests**

Run:

```bash
npm --cache ./.npm --prefix frontend test -- LearningPlanDraftPanel.test.tsx
```

Expected: PASS.

### Task 9: Frontend Extension Proposal UX

**Files:**
- Create: `frontend/src/learning-plans/LearningPlanExtensionPanel.tsx`
- Modify: `frontend/src/learning-plans/LearningPlanDetail.tsx`
- Modify: `frontend/src/i18n/locales.ts`
- Modify: `frontend/src/styles.css`
- Test: `frontend/src/learning-plans/LearningPlanExtensionPanel.test.tsx`

- [ ] **Step 1: Create extension panel component**

Create `LearningPlanExtensionPanel.tsx` with props:

```ts
interface LearningPlanExtensionPanelProps {
  loading: boolean;
  extension?: LearningPlanExtensionReadyEvent;
  onGenerate: (instruction: string) => Promise<boolean>;
  onRevise: (proposalGroupId: number, instruction: string) => Promise<boolean>;
  onApply: (proposalGroupId: number) => Promise<void>;
  onDiscard: (proposalGroupId: number) => Promise<void>;
}
```

Render:

- initial textarea and `生成扩展建议`;
- after ready event, a separate “待追加内容” block;
- each `extension.extensionDraft.newPhases` using phase/problem layout consistent with `PlanPreview`;
- revision textarea and buttons `按要求调整扩展`, `应用扩展`, `放弃`.

- [ ] **Step 2: Add detail page state**

In `LearningPlanDetail.tsx`, add state:

```ts
const [extension, setExtension] = useState<LearningPlanExtensionReadyEvent>();
const [extensionWorkEvent, setExtensionWorkEvent] = useState<AgentWorkStatusEvent>();
const [extensionLoading, setExtensionLoading] = useState(false);
const [extensionError, setExtensionError] = useState('');
```

Use service functions to stream first and next revisions. On `plan_extension_ready`, set extension. On apply success, call a new `onPlanUpdated` prop or existing parent refresh handler. If no refresh prop exists, extend `LearningPlanDetail` props with:

```ts
onPlanUpdated: () => void;
```

- [ ] **Step 3: Add discard behavior**

`discardLearningPlanExtensionProposal(plan.id, proposalGroupId)` clears `extension` and `extensionError`; it does not mutate `plan`.

- [ ] **Step 4: Add locale copy**

Add Chinese values:

```ts
extensionEntryLabel: '想继续学习？描述接下来的目标',
generateExtension: '生成扩展建议',
pendingExtensionTitle: '待追加内容',
reviseExtensionLabel: '对扩展建议不满意？输入调整要求',
reviseExtension: '按要求调整扩展',
applyExtension: '应用扩展',
discardExtension: '放弃',
extensionFailed: '生成扩展建议失败，请稍后重试。',
extensionApplyFailed: '应用扩展失败，请重新生成后再试。',
```

- [ ] **Step 5: Add tests**

`LearningPlanExtensionPanel.test.tsx` covers:

- initial textarea calls `onGenerate`;
- ready extension renders “待追加内容” and new phase/problem;
- revision calls `onRevise` with `proposalGroupId`;
- discard calls `onDiscard`;
- apply calls `onApply`.

- [ ] **Step 6: Run extension panel tests**

Run:

```bash
npm --cache ./.npm --prefix frontend test -- LearningPlanExtensionPanel.test.tsx
```

Expected: PASS.

### Task 10: End-to-End Verification

**Files:**
- All files touched by previous tasks.

- [ ] **Step 1: Run focused backend application tests**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-application -am test -Dtest=LearningPlanProposalGroupServiceTest,LearningPlanExtensionValidatorTest,LearningPlanExtensionApplyServiceTest,LearningPlanDraftRevisionStreamServiceTest,LearningPlanExtensionProposalStreamServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: PASS.

- [ ] **Step 2: Run focused backend API tests**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-api -am test -Dtest=LearningPlanControllerTest,ProblemMapperXmlTest,FlywayMigrationResourceTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: PASS.

- [ ] **Step 3: Run focused frontend tests**

Run:

```bash
npm --cache ./.npm --prefix frontend test -- api.test.ts LearningPlanDraftPanel.test.tsx LearningPlanExtensionPanel.test.tsx
```

Expected: PASS.

- [ ] **Step 4: Run full project verification**

Run:

```bash
make backend-test
make frontend-test
```

Expected: both commands pass.

- [ ] **Step 5: Inspect final diff**

Run:

```bash
git status --short
git diff --stat
```

Expected: diff only contains planned backend, frontend, migration, and test files. Existing unrelated change `docs/learning-plan-proposal-design.md` must not be reverted or folded into this implementation unless the user explicitly asks.

## Self-Review

- Spec coverage: 草案自然语言迭代由 Task 4、Task 6、Task 8 覆盖；正式计划扩展生成和调整由 Task 5、Task 6、Task 9 覆盖；应用和放弃由 Task 3、Task 6、Task 9 覆盖；append-only 风险由 Task 2 和 Task 3 覆盖；观测和治理由 Task 6 覆盖。
- Placeholder scan: 计划没有保留未展开的占位步骤；每个任务有明确文件、测试命令和期望结果。
- Type consistency: 后端统一使用 `proposalGroupId` 表示提案组，`proposalId` 表示具体 revision；前端事件类型同名；API 路径变量固定为 `{proposalGroupId}`。
