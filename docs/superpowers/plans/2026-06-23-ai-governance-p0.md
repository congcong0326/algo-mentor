# AI Governance P0 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 基于 `docs/superpowers/specs/2026-06-23-ai-governance-p0-design.md` 落地真实用户 AI 调用治理底座：用户归属、准入、共享日配额、单用户 active run、admission 持久化、指标、trace 访问控制和稳定 API 错误映射。

**Architecture:** 新增 `backend/ai-governance` Maven 模块，内部按 `model/policy/admission/usage/runlock/trace/metrics/repository.mybatis/autoconfigure` 分层；模块只依赖 `llm-core`、`agent-core`、`auth` 和基础 Spring/MyBatis/Micrometer，不依赖 `mentor-api` controller 或 `mentor-application` 业务模型。`mentor-api` 和 `mentor-application` 负责把现有学习计划、题目讲解和受限学习对话入口接入 governance，统一从认证上下文构造 `AiActor`，禁止从请求体信任 AI 治理用 `userId`。

**Tech Stack:** Java 17、Spring Boot 3.5、Spring MVC/SSE、Maven 多模块、MyBatis、Flyway、PostgreSQL、Micrometer、Jackson、JUnit 5、AssertJ、MockMvc。

---

## Current Context

- 现有 Flyway 版本号已经使用到 `V9__learning_plan_schema.sql`，本计划新增 migration 使用 `V10__ai_governance_schema.sql`，避免跨模块版本冲突。
- `mentor-api` 的 `MentorApiMyBatisConfiguration` 已配置全局 `SqlSessionFactory` 并扫描 `classpath*:mapper/**/*.xml`；`ai-governance` 不创建第二个 `SqlSessionFactory`，只通过自动配置注册 mapper、repository、service、observer。
- `agent-core` 已有 `AgentRunLockManager`、`InMemoryAgentRunLockManager`、`AgentRunLockReleaseObserver`，但当前内存锁只记录 `expiresAt`，不会在 `tryAcquire` 时清理过期锁；P0 需要先增强这点，避免 active run 遗留锁永久阻塞。
- `AgentLoopObserver` 可覆盖流式 Agent 的 `onRunStart/onLlmEvent/onRunEnd/onError/onTool*` 生命周期；同步 LLM/Agent 调用没有 observer 回调，因此 governance 需要提供显式 lifecycle service，供同步入口在 `try/finally` 中标记完成、失败或取消。
- 当前 AI 入口包括：
  - `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/AiStreamController.java`
  - `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/AgentConversationController.java`
  - `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/learningplan/LearningPlanController.java`
  - `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/ExplainTopicUseCase.java`
  - `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/conversation/AgentConversationService.java`
  - `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/conversation/AgentConversationRunCoordinator.java`
  - `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/LearningPlanDraftService.java`

## File Structure

### New Module

- Create: `backend/ai-governance/pom.xml`  
  Maven module definition. Depends on `common`, `llm-core`, `agent-core`, `auth`, Jackson, MyBatis, Spring context/tx/boot-autoconfigure, Micrometer.
- Modify: `backend/pom.xml`  
  Add `<module>ai-governance</module>` after `auth` and before `mentor-application`.
- Modify: `backend/mentor-api/pom.xml`  
  Add dependency on `ai-governance`, because `mentor-api` is the final Boot app and HTTP integration point.

### Governance Model

- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/model/AiPurpose.java`
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/model/AiRunSource.java`
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/model/AiRunStatus.java`
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/model/AiGovernanceErrorCode.java`
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/model/AiActor.java`
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/model/AiRunContext.java`
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/model/AiUsage.java`
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/model/AiGovernanceMetadataKeys.java`

### Policy And Admission

- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/policy/AiPurposePolicy.java`
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/policy/AiGovernanceProperties.java`
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/policy/AiPurposePolicyResolver.java`
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/admission/AiRunAdmission.java`
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/admission/AiRunAdmissionException.java`
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/admission/AiRunAdmissionService.java`
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/admission/AiRunLifecycleService.java`

### Usage, Run Lock, Trace, Metrics

- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/usage/AiDailyUsageStore.java`
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/usage/PostgresAiDailyUsageStore.java`
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/runlock/AiRunLockService.java`
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/trace/AiTraceAccessPolicy.java`
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/trace/AiTraceAccessDeniedException.java`
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/trace/AiTraceRedactionPolicy.java`
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/metrics/AiRunMetricsObserver.java`
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/metrics/AiRunGovernanceObserver.java`

### Repository And Migration

- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/repository/mybatis/AiRunAdmissionMapper.java`
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/repository/mybatis/AiDailyUsageMapper.java`
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/repository/mybatis/PostgresAiRunAdmissionRepository.java`
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/repository/mybatis/model/AiRunAdmissionRow.java`
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/repository/mybatis/model/AiDailyUsageRow.java`
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/repository/mybatis/model/AiRunStatusUpdate.java`
- Create: `backend/ai-governance/src/main/resources/mapper/ai/AiRunAdmissionMapper.xml`
- Create: `backend/ai-governance/src/main/resources/mapper/ai/AiDailyUsageMapper.xml`
- Create: `backend/ai-governance/src/main/resources/db/migration/ai/V10__ai_governance_schema.sql`

### Auto Configuration

- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/autoconfigure/AiGovernanceAutoConfiguration.java`
- Create: `backend/ai-governance/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

### Agent Core Adjustments

- Modify: `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/runlock/InMemoryAgentRunLockManager.java`
- Modify: `backend/agent-core/src/test/java/org/congcong/algomentor/agent/core/runlock/InMemoryAgentRunLockManagerTest.java`

### Agent Persistence Adjustments

- Modify: `backend/agent-persistence-postgres/src/main/java/org/congcong/algomentor/agent/persistence/postgres/observer/AgentTraceRedactor.java`
- Modify: `backend/agent-persistence-postgres/src/test/java/org/congcong/algomentor/agent/persistence/postgres/observer/AgentTraceRedactorTest.java`

### API And Application Integration

- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/config/ApiContractConstants.java`
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/config/MentorAiConfiguration.java`
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/config/MentorApiMyBatisConfiguration.java`
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/service/AiExplanationService.java`
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/AiStreamController.java`
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/AgentConversationController.java`
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/AgentConversationExceptionHandler.java`
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/learningplan/LearningPlanController.java`
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/learningplan/LearningPlanExceptionHandler.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/AiGovernanceExceptionHandler.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/service/AiActorResolver.java`
- Modify: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/ExplainTopicUseCase.java`
- Modify: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/conversation/AgentConversationCommand.java`
- Modify: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/conversation/AgentConversationService.java`
- Modify: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/conversation/AgentConversationRunCoordinator.java`
- Modify: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/LearningPlanDraftService.java`

### Tests

- Create: `backend/ai-governance/src/test/java/org/congcong/algomentor/ai/governance/model/AiGovernanceModelTest.java`
- Create: `backend/ai-governance/src/test/java/org/congcong/algomentor/ai/governance/policy/AiPurposePolicyResolverTest.java`
- Create: `backend/ai-governance/src/test/java/org/congcong/algomentor/ai/governance/admission/AiRunAdmissionServiceTest.java`
- Create: `backend/ai-governance/src/test/java/org/congcong/algomentor/ai/governance/usage/PostgresAiDailyUsageStoreTest.java`
- Create: `backend/ai-governance/src/test/java/org/congcong/algomentor/ai/governance/runlock/AiRunLockServiceTest.java`
- Create: `backend/ai-governance/src/test/java/org/congcong/algomentor/ai/governance/trace/AiTraceAccessPolicyTest.java`
- Create: `backend/ai-governance/src/test/java/org/congcong/algomentor/ai/governance/metrics/AiRunGovernanceObserverTest.java`
- Create: `backend/ai-governance/src/test/java/org/congcong/algomentor/ai/governance/metrics/AiRunMetricsObserverTest.java`
- Create: `backend/ai-governance/src/test/java/org/congcong/algomentor/ai/governance/repository/mybatis/AiGovernanceMapperXmlTest.java`
- Create: `backend/ai-governance/src/test/java/org/congcong/algomentor/ai/governance/repository/mybatis/AiGovernanceMigrationResourceTest.java`
- Modify: `backend/mentor-api/src/test/java/org/congcong/algomentor/api/config/FlywayMigrationResourceTest.java`
- Modify: `backend/mentor-api/src/test/java/org/congcong/algomentor/api/controller/AiStreamControllerTest.java`
- Modify: `backend/mentor-api/src/test/java/org/congcong/algomentor/api/controller/AgentConversationControllerTest.java`
- Modify: `backend/mentor-api/src/test/java/org/congcong/algomentor/api/controller/learningplan/LearningPlanControllerTest.java`

---

### Task 1: Maven Module Skeleton And Auto-Configuration Registration

**Files:**
- Modify: `backend/pom.xml`
- Modify: `backend/mentor-api/pom.xml`
- Create: `backend/ai-governance/pom.xml`
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/autoconfigure/AiGovernanceAutoConfiguration.java`
- Create: `backend/ai-governance/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Create: `backend/ai-governance/src/test/java/org/congcong/algomentor/ai/governance/autoconfigure/AiGovernanceAutoConfigurationTest.java`

- [ ] **Step 1: Write failing auto-configuration test**

Create `AiGovernanceAutoConfigurationTest`:

```java
package org.congcong.algomentor.ai.governance.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AiGovernanceAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(AiGovernanceAutoConfiguration.class));

  @Test
  void loadsWithoutDataSourceAndExposesProperties() {
    contextRunner
        .withPropertyValues("algo-mentor.ai-governance.enabled=true")
        .run(context -> {
          assertThat(context).hasSingleBean(org.congcong.algomentor.ai.governance.policy.AiGovernanceProperties.class);
          assertThat(context).hasSingleBean(org.congcong.algomentor.ai.governance.policy.AiPurposePolicyResolver.class);
        });
  }
}
```

- [ ] **Step 2: Run test to verify RED**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl ai-governance -am test -Dtest=AiGovernanceAutoConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: FAIL because `ai-governance` module and classes do not exist.

- [ ] **Step 3: Add Maven module and minimal auto-configuration**

Add `backend/ai-governance/pom.xml` with dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>org.congcong.algomentor</groupId>
    <artifactId>common</artifactId>
    <version>${project.version}</version>
  </dependency>
  <dependency>
    <groupId>org.congcong.algomentor</groupId>
    <artifactId>llm-core</artifactId>
    <version>${project.version}</version>
  </dependency>
  <dependency>
    <groupId>org.congcong.algomentor</groupId>
    <artifactId>agent-core</artifactId>
    <version>${project.version}</version>
  </dependency>
  <dependency>
    <groupId>org.congcong.algomentor</groupId>
    <artifactId>auth</artifactId>
    <version>${project.version}</version>
  </dependency>
  <dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
  </dependency>
  <dependency>
    <groupId>org.mybatis</groupId>
    <artifactId>mybatis</artifactId>
  </dependency>
  <dependency>
    <groupId>org.mybatis</groupId>
    <artifactId>mybatis-spring</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-context</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-tx</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-autoconfigure</artifactId>
  </dependency>
  <dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-core</artifactId>
  </dependency>
  <dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
  </dependency>
  <dependency>
    <groupId>org.assertj</groupId>
    <artifactId>assertj-core</artifactId>
    <scope>test</scope>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
  </dependency>
</dependencies>
```

Add `AiGovernanceAutoConfiguration`:

```java
@AutoConfiguration
@EnableConfigurationProperties(AiGovernanceProperties.class)
public class AiGovernanceAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public AiPurposePolicyResolver aiPurposePolicyResolver(AiGovernanceProperties properties) {
    return new AiPurposePolicyResolver(properties);
  }
}
```

Add `AutoConfiguration.imports`:

```text
org.congcong.algomentor.ai.governance.autoconfigure.AiGovernanceAutoConfiguration
```

- [ ] **Step 4: Run test to verify GREEN**

Run the same Maven command.

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/pom.xml backend/mentor-api/pom.xml backend/ai-governance
git commit -m "chore: add ai governance module skeleton"
```

### Task 2: Core Governance Model And Metadata Contract

**Files:**
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/model/AiPurpose.java`
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/model/AiRunSource.java`
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/model/AiRunStatus.java`
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/model/AiGovernanceErrorCode.java`
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/model/AiActor.java`
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/model/AiRunContext.java`
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/model/AiUsage.java`
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/model/AiGovernanceMetadataKeys.java`
- Create: `backend/ai-governance/src/test/java/org/congcong/algomentor/ai/governance/model/AiGovernanceModelTest.java`

- [ ] **Step 1: Write failing model tests**

Create tests:

```java
class AiGovernanceModelTest {

  @Test
  void actorDerivesAdminAndAuthenticatedFromRoles() {
    AiActor actor = new AiActor(7L, Set.of(AuthRole.USER, AuthRole.ADMIN), true);

    assertThat(actor.userId()).isEqualTo(7L);
    assertThat(actor.admin()).isTrue();
    assertThat(actor.authenticated()).isTrue();
  }

  @Test
  void anonymousActorHasNoUserIdAndIsNotAdmin() {
    AiActor actor = AiActor.anonymous();

    assertThat(actor.userId()).isNull();
    assertThat(actor.admin()).isFalse();
    assertThat(actor.authenticated()).isFalse();
  }

  @Test
  void runContextRejectsMissingRequiredFields() {
    assertThatThrownBy(() -> new AiRunContext(
        null,
        new AiActor(1L, Set.of(AuthRole.USER), true),
        AiPurpose.LEARNING_PLAN,
        AiRunSource.LEARNING_PLAN_DRAFT,
        null,
        10,
        false,
        Map.of(),
        Instant.parse("2026-06-23T00:00:00Z")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("run id");
  }

  @Test
  void usageAdditionKeepsNullSafeTokenCounters() {
    AiUsage usage = new AiUsage(1, 2, 3, 4, 10);

    assertThat(AiUsage.zero().plus(usage)).isEqualTo(usage);
  }
}
```

- [ ] **Step 2: Run test to verify RED**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl ai-governance -am test -Dtest=AiGovernanceModelTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: FAIL because model classes do not exist.

- [ ] **Step 3: Implement model classes**

Use exact enum constants:

```java
public enum AiPurpose {
  LEARNING_PLAN,
  PROBLEM_EXPLANATION,
  LEARNING_CHAT
}

public enum AiRunSource {
  LEARNING_PLAN_DRAFT,
  PROBLEM_DETAIL,
  LEARNING_CHAT,
  AI_DEBUG
}

public enum AiRunStatus {
  ADMITTED,
  REJECTED_QUOTA,
  REJECTED_CONCURRENT,
  REJECTED_DISABLED,
  REJECTED_UNAUTHENTICATED,
  REJECTED_FORBIDDEN,
  REJECTED_REQUEST_TOO_LARGE,
  RUNNING,
  COMPLETED,
  FAILED,
  CANCELLED,
  EXPIRED
}

public enum AiGovernanceErrorCode {
  AI_PROVIDER_DISABLED,
  AI_PURPOSE_DISABLED,
  AI_UNAUTHENTICATED,
  AI_FORBIDDEN,
  AI_QUOTA_EXCEEDED,
  AI_CONCURRENT_RUN_CONFLICT,
  AI_REQUEST_TOO_LARGE,
  AI_TIMEOUT,
  AI_RATE_LIMITED,
  AI_PROVIDER_UNAVAILABLE,
  AI_STRUCTURED_OUTPUT_INVALID,
  AI_CANCELLED,
  AI_UNKNOWN
}
```

Create `AiGovernanceMetadataKeys` constants:

```java
public final class AiGovernanceMetadataKeys {
  public static final String ADMISSION = "aiAdmission";
  public static final String RUN_ID = "aiRunId";
  public static final String ADMISSION_ID = "aiAdmissionId";
  public static final String USER_ID = "aiUserId";
  public static final String PURPOSE = "aiPurpose";
  public static final String SOURCE = "aiSource";
  public static final String QUOTA_SCOPE = "aiQuotaScope";
  public static final String DAILY_LIMIT = "aiDailyLimit";
  public static final String SYSTEM_POLICY_VERSION = "aiSystemPolicyVersion";
  public static final String GOVERNANCE_STATUS = "aiGovernanceStatus";

  private AiGovernanceMetadataKeys() {
  }
}
```

Create records with validation:

```java
public record AiActor(Long userId, Set<AuthRole> roles, boolean authenticated) {
  public AiActor {
    if (authenticated && (userId == null || userId < 1)) {
      throw new IllegalArgumentException("Authenticated AI actor must have a positive user id");
    }
    roles = roles == null ? Set.of() : Set.copyOf(roles);
  }

  public static AiActor anonymous() {
    return new AiActor(null, Set.of(), false);
  }

  public boolean admin() {
    return roles.contains(AuthRole.ADMIN);
  }
}
```

```java
public record AiRunContext(
    String runId,
    AiActor actor,
    AiPurpose purpose,
    AiRunSource source,
    String idempotencyKey,
    int requestSize,
    boolean streaming,
    Map<String, Object> metadata,
    Instant createdAt
) {
  public AiRunContext {
    if (runId == null || runId.isBlank()) {
      throw new IllegalArgumentException("AI run id must not be blank");
    }
    if (actor == null) {
      throw new IllegalArgumentException("AI actor must not be null");
    }
    if (purpose == null) {
      throw new IllegalArgumentException("AI purpose must not be null");
    }
    if (source == null) {
      throw new IllegalArgumentException("AI run source must not be null");
    }
    if (requestSize < 0) {
      throw new IllegalArgumentException("AI request size must not be negative");
    }
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    createdAt = createdAt == null ? Instant.now() : createdAt;
  }
}
```

```java
public record AiUsage(
    long inputTokens,
    long outputTokens,
    long cachedTokens,
    long reasoningTokens,
    long totalTokens
) {
  public static AiUsage zero() {
    return new AiUsage(0, 0, 0, 0, 0);
  }

  public AiUsage plus(AiUsage other) {
    AiUsage value = other == null ? zero() : other;
    return new AiUsage(
        inputTokens + value.inputTokens(),
        outputTokens + value.outputTokens(),
        cachedTokens + value.cachedTokens(),
        reasoningTokens + value.reasoningTokens(),
        totalTokens + value.totalTokens());
  }
}
```

- [ ] **Step 4: Run test to verify GREEN**

Run the same Maven command.

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/model backend/ai-governance/src/test/java/org/congcong/algomentor/ai/governance/model
git commit -m "feat: add ai governance model contract"
```

### Task 3: Purpose Policy Configuration

**Files:**
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/policy/AiPurposePolicy.java`
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/policy/AiGovernanceProperties.java`
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/policy/AiPurposePolicyResolver.java`
- Modify: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/autoconfigure/AiGovernanceAutoConfiguration.java`
- Create: `backend/ai-governance/src/test/java/org/congcong/algomentor/ai/governance/policy/AiPurposePolicyResolverTest.java`

- [ ] **Step 1: Write failing policy tests**

Create tests:

```java
class AiPurposePolicyResolverTest {

  @Test
  void defaultPoliciesShareDailyLimitAndAllowExpectedCapabilities() {
    AiPurposePolicyResolver resolver = new AiPurposePolicyResolver(new AiGovernanceProperties());

    AiPurposePolicy learningPlan = resolver.resolve(AiPurpose.LEARNING_PLAN);
    AiPurposePolicy explanation = resolver.resolve(AiPurpose.PROBLEM_EXPLANATION);
    AiPurposePolicy chat = resolver.resolve(AiPurpose.LEARNING_CHAT);

    assertThat(learningPlan.dailyRequestLimit()).isEqualTo(50);
    assertThat(learningPlan.maxConcurrentRunsPerUser()).isEqualTo(1);
    assertThat(learningPlan.toolsAllowed()).isTrue();
    assertThat(learningPlan.structuredOutputRequired()).isTrue();
    assertThat(explanation.streamingAllowed()).isTrue();
    assertThat(chat.systemPolicyVersion()).isEqualTo("learning-chat-p0");
  }

  @Test
  void overridesPurposePolicyFromConfigurationProperties() {
    AiGovernanceProperties properties = new AiGovernanceProperties();
    properties.getPurposes().get(AiPurpose.LEARNING_CHAT).setEnabled(false);
    properties.getPurposes().get(AiPurpose.LEARNING_CHAT).setMaxRequestBytes(2048);

    AiPurposePolicy policy = new AiPurposePolicyResolver(properties).resolve(AiPurpose.LEARNING_CHAT);

    assertThat(policy.enabled()).isFalse();
    assertThat(policy.maxRequestBytes()).isEqualTo(2048);
  }
}
```

- [ ] **Step 2: Run test to verify RED**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl ai-governance -am test -Dtest=AiPurposePolicyResolverTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: FAIL because policy classes do not exist.

- [ ] **Step 3: Implement policy records and properties**

Create immutable policy record:

```java
public record AiPurposePolicy(
    boolean enabled,
    int dailyRequestLimit,
    int maxConcurrentRunsPerUser,
    int maxRequestBytes,
    int maxOutputTokens,
    int maxSteps,
    boolean streamingAllowed,
    boolean toolsAllowed,
    boolean structuredOutputRequired,
    boolean adminOnly,
    String defaultProvider,
    String defaultModel,
    String systemPolicyVersion
) {
  public AiPurposePolicy {
    if (dailyRequestLimit < 1) {
      throw new IllegalArgumentException("Daily AI request limit must be positive");
    }
    if (maxConcurrentRunsPerUser < 1) {
      throw new IllegalArgumentException("Max concurrent AI runs per user must be positive");
    }
    if (maxRequestBytes < 1) {
      throw new IllegalArgumentException("Max AI request bytes must be positive");
    }
    if (maxOutputTokens < 1) {
      throw new IllegalArgumentException("Max output tokens must be positive");
    }
    if (maxSteps < 1) {
      throw new IllegalArgumentException("Max AI steps must be positive");
    }
  }
}
```

Create `@ConfigurationProperties(prefix = "algo-mentor.ai-governance")` properties with defaults:

```java
private boolean enabled = true;
private ZoneId quotaZone = ZoneOffset.UTC;
private Duration activeRunTtl = Duration.ofMinutes(30);
private EnumMap<AiPurpose, PurposeProperties> purposes = defaultPurposeProperties();
```

Default purpose values:

```java
LEARNING_PLAN: enabled=true, dailyRequestLimit=50, maxConcurrentRunsPerUser=1,
maxRequestBytes=32768, maxOutputTokens=4096, maxSteps=12, streamingAllowed=false,
toolsAllowed=true, structuredOutputRequired=true, adminOnly=false,
defaultProvider=null, defaultModel=null, systemPolicyVersion="learning-plan-p0"

PROBLEM_EXPLANATION: enabled=true, dailyRequestLimit=50, maxConcurrentRunsPerUser=1,
maxRequestBytes=32768, maxOutputTokens=2048, maxSteps=8, streamingAllowed=true,
toolsAllowed=true, structuredOutputRequired=false, adminOnly=false,
defaultProvider=null, defaultModel=null, systemPolicyVersion="problem-explanation-p0"

LEARNING_CHAT: enabled=true, dailyRequestLimit=50, maxConcurrentRunsPerUser=1,
maxRequestBytes=16384, maxOutputTokens=2048, maxSteps=8, streamingAllowed=true,
toolsAllowed=true, structuredOutputRequired=false, adminOnly=false,
defaultProvider=null, defaultModel=null, systemPolicyVersion="learning-chat-p0"
```

The resolver must throw `IllegalArgumentException("Unsupported AI purpose: " + purpose)` when purpose is null or absent.

- [ ] **Step 4: Run policy tests**

Run the same Maven command.

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/policy backend/ai-governance/src/test/java/org/congcong/algomentor/ai/governance/policy backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/autoconfigure/AiGovernanceAutoConfiguration.java
git commit -m "feat: add ai purpose policy configuration"
```

### Task 4: PostgreSQL Migration, MyBatis Mappers, And Daily Usage Store

**Files:**
- Create: `backend/ai-governance/src/main/resources/db/migration/ai/V10__ai_governance_schema.sql`
- Create: `backend/ai-governance/src/main/resources/mapper/ai/AiDailyUsageMapper.xml`
- Create: `backend/ai-governance/src/main/resources/mapper/ai/AiRunAdmissionMapper.xml`
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/repository/mybatis/AiDailyUsageMapper.java`
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/repository/mybatis/AiRunAdmissionMapper.java`
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/repository/mybatis/model/AiDailyUsageRow.java`
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/repository/mybatis/model/AiRunAdmissionRow.java`
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/repository/mybatis/model/AiRunStatusUpdate.java`
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/usage/AiDailyUsageStore.java`
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/usage/PostgresAiDailyUsageStore.java`
- Create: `backend/ai-governance/src/test/java/org/congcong/algomentor/ai/governance/repository/mybatis/AiGovernanceMapperXmlTest.java`
- Create: `backend/ai-governance/src/test/java/org/congcong/algomentor/ai/governance/repository/mybatis/AiGovernanceMigrationResourceTest.java`
- Create: `backend/ai-governance/src/test/java/org/congcong/algomentor/ai/governance/usage/PostgresAiDailyUsageStoreTest.java`
- Modify: `backend/mentor-api/src/test/java/org/congcong/algomentor/api/config/FlywayMigrationResourceTest.java`

- [ ] **Step 1: Write failing migration and mapper tests**

Create `AiGovernanceMigrationResourceTest`:

```java
class AiGovernanceMigrationResourceTest {

  @Test
  void migrationDefinesAdmissionAndDailyUsageTables() throws IOException {
    Resource resource = new PathMatchingResourcePatternResolver()
        .getResource("classpath:db/migration/ai/V10__ai_governance_schema.sql");

    assertThat(resource.exists()).isTrue();
    String sql = resource.getContentAsString(StandardCharsets.UTF_8);
    assertThat(sql).contains("CREATE TABLE IF NOT EXISTS ai_run_admissions");
    assertThat(sql).contains("CREATE TABLE IF NOT EXISTS ai_daily_usage");
    assertThat(sql).contains("UNIQUE (run_id)");
    assertThat(sql).contains("UNIQUE (user_id, quota_date, scope)");
  }
}
```

Create `AiGovernanceMapperXmlTest` that parses both XML files:

```java
class AiGovernanceMapperXmlTest {

  @Test
  void mapperXmlFilesParse() throws Exception {
    org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
    configuration.setMapUnderscoreToCamelCase(true);
    for (String mapper : List.of(
        "mapper/ai/AiDailyUsageMapper.xml",
        "mapper/ai/AiRunAdmissionMapper.xml")) {
      try (InputStream input = getClass().getClassLoader().getResourceAsStream(mapper)) {
        assertThat(input).as(mapper).isNotNull();
        new XMLMapperBuilder(input, configuration, mapper, configuration.getSqlFragments()).parse();
      }
    }
  }
}
```

Modify `FlywayMigrationResourceTest` assertion to include version `10`:

```java
assertThat(migrationsByVersion).containsKeys("8", "10");
```

- [ ] **Step 2: Write failing usage store tests**

Create `PostgresAiDailyUsageStoreTest` with a fake mapper:

```java
class PostgresAiDailyUsageStoreTest {

  @Test
  void incrementsSharedDailyRequestWithinLimit() {
    FakeDailyUsageMapper mapper = new FakeDailyUsageMapper();
    PostgresAiDailyUsageStore store = new PostgresAiDailyUsageStore(mapper);

    boolean admitted = store.tryConsumeRequest(7L, LocalDate.parse("2026-06-23"), "ALL", 50);

    assertThat(admitted).isTrue();
    assertThat(mapper.row.requestCount()).isEqualTo(1);
    assertThat(mapper.row.limitCount()).isEqualTo(50);
  }

  @Test
  void rejectsWhenDailyRequestLimitReached() {
    FakeDailyUsageMapper mapper = new FakeDailyUsageMapper();
    mapper.row = new AiDailyUsageRow(null, 7L, LocalDate.parse("2026-06-23"), "ALL", 50, 0, 0, 0, 0, 0, 50, null);
    PostgresAiDailyUsageStore store = new PostgresAiDailyUsageStore(mapper);

    boolean admitted = store.tryConsumeRequest(7L, LocalDate.parse("2026-06-23"), "ALL", 50);

    assertThat(admitted).isFalse();
    assertThat(mapper.row.requestCount()).isEqualTo(50);
  }

  @Test
  void accumulatesTokenUsageAfterRunCompletion() {
    FakeDailyUsageMapper mapper = new FakeDailyUsageMapper();
    mapper.row = new AiDailyUsageRow(null, 7L, LocalDate.parse("2026-06-23"), "ALL", 1, 0, 0, 0, 0, 0, 50, null);
    PostgresAiDailyUsageStore store = new PostgresAiDailyUsageStore(mapper);

    store.addUsage(7L, LocalDate.parse("2026-06-23"), "ALL", new AiUsage(10, 20, 3, 4, 34));

    assertThat(mapper.row.inputTokens()).isEqualTo(10);
    assertThat(mapper.row.outputTokens()).isEqualTo(20);
    assertThat(mapper.row.cachedTokens()).isEqualTo(3);
    assertThat(mapper.row.reasoningTokens()).isEqualTo(4);
    assertThat(mapper.row.totalTokens()).isEqualTo(34);
  }
}
```

- [ ] **Step 3: Run tests to verify RED**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl ai-governance,mentor-api -am test -Dtest=AiGovernanceMigrationResourceTest,AiGovernanceMapperXmlTest,PostgresAiDailyUsageStoreTest,FlywayMigrationResourceTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: FAIL because migration, mapper, and store do not exist.

- [ ] **Step 4: Add migration SQL**

Create `V10__ai_governance_schema.sql`:

```sql
CREATE TABLE IF NOT EXISTS ai_run_admissions (
  id BIGSERIAL PRIMARY KEY,
  run_id VARCHAR(80) NOT NULL,
  user_id BIGINT,
  purpose VARCHAR(64) NOT NULL,
  source VARCHAR(64) NOT NULL,
  status VARCHAR(64) NOT NULL,
  idempotency_key VARCHAR(160),
  request_size INTEGER NOT NULL DEFAULT 0,
  rejection_code VARCHAR(80),
  error_code VARCHAR(80),
  provider VARCHAR(80),
  model VARCHAR(160),
  input_tokens BIGINT NOT NULL DEFAULT 0,
  output_tokens BIGINT NOT NULL DEFAULT 0,
  cached_tokens BIGINT NOT NULL DEFAULT 0,
  reasoning_tokens BIGINT NOT NULL DEFAULT 0,
  total_tokens BIGINT NOT NULL DEFAULT 0,
  started_at TIMESTAMPTZ,
  completed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_ai_run_admissions_run_id UNIQUE (run_id)
);

CREATE INDEX IF NOT EXISTS idx_ai_run_admissions_user_created_at
  ON ai_run_admissions (user_id, created_at);

CREATE INDEX IF NOT EXISTS idx_ai_run_admissions_purpose_created_at
  ON ai_run_admissions (purpose, created_at);

CREATE INDEX IF NOT EXISTS idx_ai_run_admissions_status_created_at
  ON ai_run_admissions (status, created_at);

CREATE TABLE IF NOT EXISTS ai_daily_usage (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  quota_date DATE NOT NULL,
  scope VARCHAR(64) NOT NULL,
  request_count BIGINT NOT NULL DEFAULT 0,
  input_tokens BIGINT NOT NULL DEFAULT 0,
  output_tokens BIGINT NOT NULL DEFAULT 0,
  cached_tokens BIGINT NOT NULL DEFAULT 0,
  reasoning_tokens BIGINT NOT NULL DEFAULT 0,
  total_tokens BIGINT NOT NULL DEFAULT 0,
  limit_count BIGINT NOT NULL,
  token_limit BIGINT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_ai_daily_usage_user_date_scope UNIQUE (user_id, quota_date, scope)
);

CREATE INDEX IF NOT EXISTS idx_ai_daily_usage_user_date
  ON ai_daily_usage (user_id, quota_date);
```

- [ ] **Step 5: Implement mapper contracts and XML**

`AiDailyUsageMapper` methods:

```java
int insertIfAbsent(@Param("userId") long userId, @Param("quotaDate") LocalDate quotaDate,
    @Param("scope") String scope, @Param("limitCount") long limitCount);

int incrementRequestIfWithinLimit(@Param("userId") long userId, @Param("quotaDate") LocalDate quotaDate,
    @Param("scope") String scope, @Param("limitCount") long limitCount);

int addUsage(@Param("userId") long userId, @Param("quotaDate") LocalDate quotaDate,
    @Param("scope") String scope, @Param("usage") AiUsage usage);
```

`incrementRequestIfWithinLimit` SQL must be atomic:

```xml
<update id="incrementRequestIfWithinLimit">
  UPDATE ai_daily_usage
  SET request_count = request_count + 1,
      limit_count = #{limitCount},
      updated_at = now()
  WHERE user_id = #{userId}
    AND quota_date = #{quotaDate}
    AND scope = #{scope}
    AND request_count &lt; #{limitCount}
</update>
```

`AiRunAdmissionMapper` methods:

```java
long insertAdmission(AiRunAdmissionRow row);
int updateStatus(AiRunStatusUpdate update);
AiRunAdmissionRow findByRunId(@Param("runId") String runId);
```

`AiRunStatusUpdate` contains:

```java
Long admissionId,
String runId,
AiRunStatus status,
String errorCode,
String provider,
String model,
AiUsage usage,
Instant completedAt
```

- [ ] **Step 6: Implement usage store**

`PostgresAiDailyUsageStore.tryConsumeRequest`:

```java
@Transactional
public boolean tryConsumeRequest(long userId, LocalDate quotaDate, String scope, long limitCount) {
  mapper.insertIfAbsent(userId, quotaDate, scope, limitCount);
  return mapper.incrementRequestIfWithinLimit(userId, quotaDate, scope, limitCount) == 1;
}
```

`addUsage` delegates to mapper and treats null usage as `AiUsage.zero()`.

- [ ] **Step 7: Run tests to verify GREEN**

Run the same Maven command.

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add backend/ai-governance/src/main/resources backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/repository backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/usage backend/ai-governance/src/test/java/org/congcong/algomentor/ai/governance backend/mentor-api/src/test/java/org/congcong/algomentor/api/config/FlywayMigrationResourceTest.java
git commit -m "feat: add ai governance persistence"
```

### Task 5: Active Run Lock TTL Enhancement And Governance Lock Adapter

**Files:**
- Modify: `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/runlock/InMemoryAgentRunLockManager.java`
- Modify: `backend/agent-core/src/test/java/org/congcong/algomentor/agent/core/runlock/InMemoryAgentRunLockManagerTest.java`
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/runlock/AiRunLockService.java`
- Create: `backend/ai-governance/src/test/java/org/congcong/algomentor/ai/governance/runlock/AiRunLockServiceTest.java`

- [ ] **Step 1: Write failing run lock tests**

Add test to `InMemoryAgentRunLockManagerTest`:

```java
@Test
void replacesExpiredLockOnNextAcquire() {
  InMemoryAgentRunLockManager manager = new InMemoryAgentRunLockManager();
  AgentRunLockRequest request = new AgentRunLockRequest(
      "user:7:ai:all",
      "owner-1",
      Duration.ofMillis(1),
      Map.of("runId", "old"));

  AgentRunLockToken oldToken = manager.tryAcquire(request).token();
  await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
      assertThat(Instant.now()).isAfter(oldToken.expiresAt()));

  AgentRunLockAcquireResult result = manager.tryAcquire(new AgentRunLockRequest(
      "user:7:ai:all",
      "owner-2",
      Duration.ofMinutes(30),
      Map.of("runId", "new")));

  assertThat(result.acquired()).isTrue();
  assertThat(result.token().ownerId()).isEqualTo("owner-2");
}
```

Use `Thread.sleep(10)` in this unit test to avoid introducing a new Awaitility dependency for one TTL assertion.

Create `AiRunLockServiceTest`:

```java
class AiRunLockServiceTest {

  @Test
  void usesPerUserAllAiLockKey() {
    InMemoryAgentRunLockManager manager = new InMemoryAgentRunLockManager();
    AiRunLockService service = new AiRunLockService(manager, () -> "node-1", Duration.ofMinutes(30));

    AgentRunLockToken token = service.tryAcquire(7L, "run-1", Map.of("purpose", "LEARNING_CHAT"))
        .orElseThrow();

    assertThat(token.lockKey()).isEqualTo("user:7:ai:all");
    assertThat(token.ownerId()).isEqualTo("node-1");
    manager.release(token);
  }

  @Test
  void returnsEmptyWhenSameUserAlreadyHasActiveAiRun() {
    InMemoryAgentRunLockManager manager = new InMemoryAgentRunLockManager();
    AiRunLockService service = new AiRunLockService(manager, () -> "node-1", Duration.ofMinutes(30));
    AgentRunLockToken token = service.tryAcquire(7L, "run-1", Map.of()).orElseThrow();

    assertThat(service.tryAcquire(7L, "run-2", Map.of())).isEmpty();

    manager.release(token);
  }
}
```

- [ ] **Step 2: Run tests to verify RED**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl agent-core,ai-governance -am test -Dtest=InMemoryAgentRunLockManagerTest,AiRunLockServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: FAIL because expired lock is not replaced and `AiRunLockService` does not exist.

- [ ] **Step 3: Enhance in-memory lock expiration**

In `tryAcquire`, before returning conflict, replace expired existing entry:

```java
Instant now = Instant.now();
String tokenId = UUID.randomUUID().toString();
Instant expiresAt = request.ttl() == null ? null : now.plus(request.ttl());
LockEntry newEntry = new LockEntry(request.ownerId(), tokenId, expiresAt, request.metadata());
AtomicReference<LockEntry> conflict = new AtomicReference<>();
locks.compute(request.lockKey(), (ignored, existing) -> {
  if (existing == null || existing.expiredAt(now)) {
    return newEntry;
  }
  conflict.set(existing);
  return existing;
});
if (conflict.get() == null) {
  return AgentRunLockAcquireResult.acquired(new AgentRunLockToken(
      request.lockKey(), request.ownerId(), tokenId, expiresAt));
}
LockEntry existing = conflict.get();
return AgentRunLockAcquireResult.conflicted(new AgentRunLockConflict(
    request.lockKey(), existing.ownerId(), existing.expiresAt(), existing.metadata()));
```

Add method:

```java
private boolean expiredAt(Instant now) {
  return expiresAt != null && !expiresAt.isAfter(now);
}
```

- [ ] **Step 4: Implement `AiRunLockService`**

Use constants inside the class:

```java
private static final String AI_LOCK_KEY_PREFIX = "user:";
private static final String AI_LOCK_KEY_SUFFIX = ":ai:all";
```

Method contract:

```java
public Optional<AgentRunLockToken> tryAcquire(long userId, String runId, Map<String, Object> metadata)
public void release(AgentRunLockToken token)
public String lockKey(long userId)
```

`tryAcquire` builds:

```java
Map<String, Object> lockMetadata = new LinkedHashMap<>();
lockMetadata.put(AiGovernanceMetadataKeys.USER_ID, userId);
lockMetadata.put(AiGovernanceMetadataKeys.RUN_ID, runId);
lockMetadata.putAll(metadata == null ? Map.of() : metadata);
return lockManager.tryAcquire(new AgentRunLockRequest(lockKey(userId), ownerProvider.ownerId(), ttl, lockMetadata))
    .acquired() ? Optional.of(result.token()) : Optional.empty();
```

- [ ] **Step 5: Run tests to verify GREEN**

Run the same Maven command.

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/runlock/InMemoryAgentRunLockManager.java backend/agent-core/src/test/java/org/congcong/algomentor/agent/core/runlock/InMemoryAgentRunLockManagerTest.java backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/runlock backend/ai-governance/src/test/java/org/congcong/algomentor/ai/governance/runlock
git commit -m "feat: add ai active run lock adapter"
```

### Task 6: Admission Service And Failure Semantics

**Files:**
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/admission/AiRunAdmission.java`
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/admission/AiRunAdmissionException.java`
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/admission/AiRunAdmissionService.java`
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/admission/AiRunLifecycleService.java`
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/repository/mybatis/PostgresAiRunAdmissionRepository.java`
- Create: `backend/ai-governance/src/test/java/org/congcong/algomentor/ai/governance/admission/AiRunAdmissionServiceTest.java`

- [ ] **Step 1: Write failing admission tests**

Create tests for exact check order:

```java
class AiRunAdmissionServiceTest {

  @Test
  void rejectsDisabledPurposeBeforeAuthenticationCheck() {
    Fixture fixture = new Fixture();
    fixture.properties.getPurposes().get(AiPurpose.LEARNING_CHAT).setEnabled(false);

    AiRunAdmissionException ex = catchThrowableOfType(
        () -> fixture.service.admit(fixture.context(AiActor.anonymous(), AiPurpose.LEARNING_CHAT, 10)),
        AiRunAdmissionException.class);

    assertThat(ex.code()).isEqualTo(AiGovernanceErrorCode.AI_PURPOSE_DISABLED);
    assertThat(fixture.usage.consumeCalls).isZero();
    assertThat(fixture.locks.acquireCalls).isZero();
  }

  @Test
  void rejectsUnauthenticatedActor() {
    Fixture fixture = new Fixture();

    AiRunAdmissionException ex = catchThrowableOfType(
        () -> fixture.service.admit(fixture.context(AiActor.anonymous(), AiPurpose.LEARNING_PLAN, 10)),
        AiRunAdmissionException.class);

    assertThat(ex.code()).isEqualTo(AiGovernanceErrorCode.AI_UNAUTHENTICATED);
  }

  @Test
  void rejectsRequestTooLargeBeforeQuotaAndLock() {
    Fixture fixture = new Fixture();
    fixture.properties.getPurposes().get(AiPurpose.LEARNING_PLAN).setMaxRequestBytes(5);

    AiRunAdmissionException ex = catchThrowableOfType(
        () -> fixture.service.admit(fixture.context(fixture.user(), AiPurpose.LEARNING_PLAN, 6)),
        AiRunAdmissionException.class);

    assertThat(ex.code()).isEqualTo(AiGovernanceErrorCode.AI_REQUEST_TOO_LARGE);
    assertThat(fixture.usage.consumeCalls).isZero();
    assertThat(fixture.locks.acquireCalls).isZero();
  }

  @Test
  void rejectsQuotaBeforeLock() {
    Fixture fixture = new Fixture();
    fixture.usage.consumeResult = false;

    AiRunAdmissionException ex = catchThrowableOfType(
        () -> fixture.service.admit(fixture.context(fixture.user(), AiPurpose.LEARNING_PLAN, 10)),
        AiRunAdmissionException.class);

    assertThat(ex.code()).isEqualTo(AiGovernanceErrorCode.AI_QUOTA_EXCEEDED);
    assertThat(fixture.locks.acquireCalls).isZero();
  }

  @Test
  void rejectsConcurrentRunAfterQuotaConsumed() {
    Fixture fixture = new Fixture();
    fixture.locks.result = Optional.empty();

    AiRunAdmissionException ex = catchThrowableOfType(
        () -> fixture.service.admit(fixture.context(fixture.user(), AiPurpose.LEARNING_PLAN, 10)),
        AiRunAdmissionException.class);

    assertThat(ex.code()).isEqualTo(AiGovernanceErrorCode.AI_CONCURRENT_RUN_CONFLICT);
    assertThat(fixture.usage.consumeCalls).isEqualTo(1);
  }

  @Test
  void admitsAndReturnsMetadataForAgentRequest() {
    Fixture fixture = new Fixture();

    AiRunAdmission admission = fixture.service.admit(
        fixture.context(fixture.user(), AiPurpose.PROBLEM_EXPLANATION, 10));

    assertThat(admission.metadata())
        .containsEntry(AiGovernanceMetadataKeys.RUN_ID, "run-1")
        .containsEntry(AiGovernanceMetadataKeys.PURPOSE, "PROBLEM_EXPLANATION")
        .containsEntry(AiGovernanceMetadataKeys.QUOTA_SCOPE, "ALL");
    assertThat(admission.status()).isEqualTo(AiRunStatus.ADMITTED);
  }
}
```

- [ ] **Step 2: Run test to verify RED**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl ai-governance -am test -Dtest=AiRunAdmissionServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: FAIL because admission service does not exist.

- [ ] **Step 3: Implement admission exception and admission record**

`AiRunAdmissionException` fields:

```java
AiGovernanceErrorCode code;
AiRunStatus status;
HttpStatus suggestedStatus;
Map<String, Object> metadata;
```

Message mapping:

```java
AI_PURPOSE_DISABLED -> "AI 功能暂未开放。"
AI_UNAUTHENTICATED -> "当前请求未登录或无法解析当前用户。"
AI_FORBIDDEN -> "当前账号无权使用该 AI 功能。"
AI_QUOTA_EXCEEDED -> "今日 AI 使用次数已达上限，请明天再试。"
AI_CONCURRENT_RUN_CONFLICT -> "已有一个 AI 任务正在运行，请等待完成后再试。"
AI_REQUEST_TOO_LARGE -> "请求内容过长，请精简后再试。"
```

`AiRunAdmission` contains:

```java
Long admissionId;
String runId;
long userId;
AiPurpose purpose;
AiRunSource source;
AiRunStatus status;
String quotaScope;
AgentRunLockToken lockToken;
AiPurposePolicy policy;
Map<String, Object> metadata;
Instant admittedAt;
```

- [ ] **Step 4: Implement admission service with fixed order**

`admit(context)` order:

1. Resolve purpose policy.
2. Reject disabled purpose with `REJECTED_DISABLED`.
3. Reject unauthenticated actor with `REJECTED_UNAUTHENTICATED`.
4. Reject admin-only policy for non-admin with `REJECTED_FORBIDDEN`.
5. Reject request size above policy with `REJECTED_REQUEST_TOO_LARGE`.
6. Consume daily request: `usageStore.tryConsumeRequest(userId, today, "ALL", policy.dailyRequestLimit())`.
7. Acquire lock: `runLockService.tryAcquire(userId, context.runId(), metadata)`.
8. Insert admitted row and return admission metadata.

For rejection after purpose/source are known, insert `ai_run_admissions` with matching rejected status and `rejection_code`.

Daily quota date uses `LocalDate.now(properties.getQuotaZone())`.

- [ ] **Step 5: Implement lifecycle service**

`AiRunLifecycleService` methods:

```java
public void markRunning(AiRunAdmission admission, String provider, String model)
public void markCompleted(AiRunAdmission admission, AiUsage usage, String provider, String model)
public void markFailed(AiRunAdmission admission, AiGovernanceErrorCode errorCode, AiUsage usage, String provider, String model)
public void markCancelled(AiRunAdmission admission, AiUsage usage, String provider, String model)
public void release(AiRunAdmission admission)
```

Completion/failure/cancellation must:

1. Update `ai_run_admissions.status`.
2. Add token usage to `ai_daily_usage`.
3. Release active run lock in `finally`.

- [ ] **Step 6: Run tests to verify GREEN**

Run the same Maven command.

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/admission backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/repository/mybatis/PostgresAiRunAdmissionRepository.java backend/ai-governance/src/test/java/org/congcong/algomentor/ai/governance/admission
git commit -m "feat: add ai run admission service"
```

### Task 7: Governance Observer And Metrics

**Files:**
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/metrics/AiRunGovernanceObserver.java`
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/metrics/AiRunMetricsObserver.java`
- Create: `backend/ai-governance/src/test/java/org/congcong/algomentor/ai/governance/metrics/AiRunGovernanceObserverTest.java`
- Create: `backend/ai-governance/src/test/java/org/congcong/algomentor/ai/governance/metrics/AiRunMetricsObserverTest.java`

- [ ] **Step 1: Write failing governance observer tests**

Create test:

```java
@Test
void updatesRunningCompletedUsageAndReleasesLockFromAgentLifecycle() {
  RecordingLifecycleService lifecycle = new RecordingLifecycleService();
  AiRunGovernanceObserver observer = new AiRunGovernanceObserver(lifecycle);
  AiRunAdmission admission = admittedRun();
  AgentLoopContext context = contextWithAdmission(admission);

  observer.onRunStart(context);
  observer.onLlmEvent(context, 1, new LlmStreamEvent.MessageStart(
      LlmProviderId.of("openai"),
      LlmModelId.of("gpt-4.1-mini")));
  observer.onLlmEvent(context, 1, new LlmStreamEvent.Usage(new LlmUsage(10, 20, 0, 0, 30)));
  observer.onRunEnd(context, new AgentRunResult(1, LlmFinishReason.STOP, Map.of()));

  assertThat(lifecycle.events).containsExactly("running", "completed");
  assertThat(lifecycle.lastUsage.totalTokens()).isEqualTo(30);
  assertThat(lifecycle.released).isTrue();
}

@Test
void mapsAgentCancellationToCancelledStatus() {
  RecordingLifecycleService lifecycle = new RecordingLifecycleService();
  AiRunGovernanceObserver observer = new AiRunGovernanceObserver(lifecycle);
  AgentLoopContext context = contextWithAdmission(admittedRun());

  observer.onError(context, new AgentException(AgentErrorCode.CANCELLED, "cancelled"));

  assertThat(lifecycle.events).containsExactly("cancelled");
  assertThat(lifecycle.released).isTrue();
}
```

- [ ] **Step 2: Write failing metrics tests**

Use `SimpleMeterRegistry`:

```java
@Test
void recordsRunCountersDurationTokensAndRejections() {
  SimpleMeterRegistry registry = new SimpleMeterRegistry();
  AiRunMetricsObserver observer = new AiRunMetricsObserver(registry);
  AiRunAdmission admission = admittedRun();
  AgentLoopContext context = contextWithAdmission(admission);

  observer.onRunStart(context);
  observer.onLlmEvent(context, 1, new LlmStreamEvent.Usage(new LlmUsage(5, 8, 0, 1, 14)));
  observer.onRunEnd(context, new AgentRunResult(1, LlmFinishReason.STOP, Map.of()));

  assertThat(registry.find("ai.run.requests").tag("status", "COMPLETED").counter().count()).isEqualTo(1);
  assertThat(registry.find("ai.run.tokens").tag("type", "total").counter().count()).isEqualTo(14);
}
```

- [ ] **Step 3: Run tests to verify RED**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl ai-governance -am test -Dtest=AiRunGovernanceObserverTest,AiRunMetricsObserverTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: FAIL because observers do not exist.

- [ ] **Step 4: Implement governance observer**

Observer behavior:

- Read `AiRunAdmission` from metadata key `AiGovernanceMetadataKeys.ADMISSION`. The primitive keys such as `ADMISSION_ID` and `RUN_ID` remain in metadata for persistence, logs, SSE and cross-layer correlation, but lifecycle release needs the in-process admission object because it contains the lock token.
- `onRunStart`: call `markRunning`.
- `onLlmEvent(MessageStart)`: remember provider/model per run id.
- `onLlmEvent(Usage)`: accumulate `AiUsage` per run id.
- `onRunEnd`: call `markCompleted` and clear buffer.
- `onError`:
  - `AgentErrorCode.CANCELLED` -> `markCancelled`.
  - `AgentErrorCode.STRUCTURED_OUTPUT_INVALID` -> `markFailed(... AI_STRUCTURED_OUTPUT_INVALID ...)`.
  - Otherwise -> `markFailed(... AI_UNKNOWN ...)`.
- Always release lock through lifecycle service in completion/failure/cancellation path.

- [ ] **Step 5: Implement metrics observer**

Metric names and tags:

```text
ai.run.requests tags: purpose, source, status, provider, model
ai.run.duration tags: purpose, source, status
ai.run.errors tags: error_code, purpose
ai.run.tokens tags: purpose, source, type
ai.run.active tags: purpose, source
ai.run.rejections tags: purpose, source, rejection_code
ai.tool.calls tags: purpose, source, tool
ai.tool.errors tags: purpose, source, tool
ai.sse.cancelled tags: purpose, source
```

Do not tag metrics with prompt, response, user input, `runId`, `userId`, idempotency key, or exception message.

- [ ] **Step 6: Run tests to verify GREEN**

Run the same Maven command.

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/metrics backend/ai-governance/src/test/java/org/congcong/algomentor/ai/governance/metrics
git commit -m "feat: add ai governance observers"
```

### Task 8: Trace Redaction And Admin Access Policy

**Files:**
- Modify: `backend/agent-persistence-postgres/src/main/java/org/congcong/algomentor/agent/persistence/postgres/observer/AgentTraceRedactor.java`
- Create: `backend/agent-persistence-postgres/src/test/java/org/congcong/algomentor/agent/persistence/postgres/observer/AgentTraceRedactorTest.java`
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/trace/AiTraceAccessPolicy.java`
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/trace/AiTraceAccessDeniedException.java`
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/trace/AiTraceRedactionPolicy.java`
- Create: `backend/ai-governance/src/test/java/org/congcong/algomentor/ai/governance/trace/AiTraceAccessPolicyTest.java`

- [ ] **Step 1: Write failing redaction tests**

Create package-private test in same observer package:

```java
class AgentTraceRedactorTest {

  @Test
  void redactsCredentialHeadersTokensPasswordsAndSecretValues() {
    ObjectMapper mapper = new ObjectMapper();
    AgentTraceRedactor redactor = new AgentTraceRedactor(mapper);
    JsonNode input = mapper.valueToTree(Map.of(
        "Authorization", "Bearer abc.def.ghi",
        "openai_api_key", "sk-test",
        "oauthToken", "oauth-secret",
        "databasePassword", "db-secret",
        "headers", Map.of("Cookie", "SESSION=abc"),
        "normal", "keep"));

    JsonNode redacted = redactor.redact(input);

    assertThat(redacted.get("Authorization").asText()).isEqualTo("[REDACTED]");
    assertThat(redacted.get("openai_api_key").asText()).isEqualTo("[REDACTED]");
    assertThat(redacted.get("oauthToken").asText()).isEqualTo("[REDACTED]");
    assertThat(redacted.get("databasePassword").asText()).isEqualTo("[REDACTED]");
    assertThat(redacted.get("headers").get("Cookie").asText()).isEqualTo("[REDACTED]");
    assertThat(redacted.get("normal").asText()).isEqualTo("keep");
  }
}
```

- [ ] **Step 2: Write failing trace access policy tests**

```java
class AiTraceAccessPolicyTest {

  @Test
  void allowsAdminToReadFullTrace() {
    AiTraceAccessPolicy policy = new AiTraceAccessPolicy();
    AiActor admin = new AiActor(1L, Set.of(AuthRole.ADMIN), true);

    assertThatCode(() -> policy.assertCanReadFullTrace(admin)).doesNotThrowAnyException();
  }

  @Test
  void rejectsNonAdminEvenForAuthenticatedUser() {
    AiTraceAccessPolicy policy = new AiTraceAccessPolicy();
    AiActor user = new AiActor(2L, Set.of(AuthRole.USER), true);

    assertThatThrownBy(() -> policy.assertCanReadFullTrace(user))
        .isInstanceOf(AiTraceAccessDeniedException.class)
        .hasMessageContaining("Only administrators can read full AI trace content");
  }
}
```

- [ ] **Step 3: Run tests to verify RED**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl agent-persistence-postgres,ai-governance -am test -Dtest=AgentTraceRedactorTest,AiTraceAccessPolicyTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: FAIL because the trace access policy classes do not exist and the redaction coverage is not yet tested.

- [ ] **Step 4: Expand redaction coverage**

Keep `AgentTraceRedactor.POLICY_VERSION` at `agent-trace-redaction-v1`; this task expands tests and matching coverage without changing the serialized policy version.

Sensitive field matching must cover:

```text
apikey, api_key, api-key, authorization, cookie, set-cookie, jwt, bearer, token,
access_token, refresh_token, oauth, password, passwd, secret, client_secret,
databasepassword, database_password, db_password, openai_api_key
```

Do not redact arbitrary content fields unless the field name is sensitive; P0 spec requires credential redaction, not prompt deletion.

- [ ] **Step 5: Implement access policy**

`AiTraceAccessPolicy.assertCanReadFullTrace`:

```java
if (actor == null || !actor.authenticated() || !actor.admin()) {
  throw new AiTraceAccessDeniedException("Only administrators can read full AI trace content");
}
```

`AiTraceRedactionPolicy` exposes:

```java
public String policyVersion()
public Set<String> sensitiveFieldHints()
```

This documents the governance-level redaction policy without forcing `agent-persistence-postgres` to depend on `ai-governance`.

- [ ] **Step 6: Run tests to verify GREEN**

Run the same Maven command.

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/agent-persistence-postgres/src/main/java/org/congcong/algomentor/agent/persistence/postgres/observer/AgentTraceRedactor.java backend/agent-persistence-postgres/src/test/java/org/congcong/algomentor/agent/persistence/postgres/observer/AgentTraceRedactorTest.java backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/trace backend/ai-governance/src/test/java/org/congcong/algomentor/ai/governance/trace
git commit -m "feat: enforce ai trace redaction and access policy"
```

### Task 9: Auto-Configure Governance Beans Into The Boot App

**Files:**
- Modify: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/autoconfigure/AiGovernanceAutoConfiguration.java`
- Modify: `backend/ai-governance/src/test/java/org/congcong/algomentor/ai/governance/autoconfigure/AiGovernanceAutoConfigurationTest.java`
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/config/MentorAiConfiguration.java`

- [ ] **Step 1: Extend failing auto-configuration tests**

Add test:

```java
@Test
void configuresGovernanceServicesWhenMyBatisAndLockBeansAreAvailable() {
  contextRunner
      .withBean(AiDailyUsageMapper.class, FakeAiDailyUsageMapper::new)
      .withBean(AiRunAdmissionMapper.class, FakeAiRunAdmissionMapper::new)
      .withBean(AgentRunLockManager.class, InMemoryAgentRunLockManager::new)
      .withBean(AgentRunLockOwnerProvider.class, () -> () -> "node-1")
      .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
      .run(context -> {
        assertThat(context).hasSingleBean(AiDailyUsageStore.class);
        assertThat(context).hasSingleBean(AiRunAdmissionService.class);
        assertThat(context).hasSingleBean(AiRunLifecycleService.class);
        assertThat(context).hasSingleBean(AiRunGovernanceObserver.class);
        assertThat(context).hasSingleBean(AiRunMetricsObserver.class);
      });
}
```

- [ ] **Step 2: Run test to verify RED**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl ai-governance -am test -Dtest=AiGovernanceAutoConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: FAIL because beans are not wired.

- [ ] **Step 3: Wire beans conditionally**

In `AiGovernanceAutoConfiguration`, register:

```java
@Bean
@ConditionalOnBean(SqlSessionTemplate.class)
@ConditionalOnMissingBean
public AiDailyUsageMapper aiDailyUsageMapper(SqlSessionTemplate template) {
  return template.getMapper(AiDailyUsageMapper.class);
}

@Bean
@ConditionalOnBean(SqlSessionTemplate.class)
@ConditionalOnMissingBean
public AiRunAdmissionMapper aiRunAdmissionMapper(SqlSessionTemplate template) {
  return template.getMapper(AiRunAdmissionMapper.class);
}
```

Register repository/store/services/observers with `@ConditionalOnMissingBean`, and require existing lock manager/owner provider for `AiRunLockService`.

- [ ] **Step 4: Ensure `AgentLoopRunner` receives governance observers**

`MentorAiConfiguration.agentLoopRunner` already accepts `List<AgentLoopObserver> observers`, so no constructor change is required. The new `AiRunGovernanceObserver` and `AiRunMetricsObserver` beans will be included automatically.

Verify `AgentRunLockReleaseObserver` remains registered. It releases task-level locks. Governance observer releases user-level AI locks. Metadata keys are different, so both observers can coexist.

- [ ] **Step 5: Run test to verify GREEN**

Run the same Maven command.

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/autoconfigure/AiGovernanceAutoConfiguration.java backend/ai-governance/src/test/java/org/congcong/algomentor/ai/governance/autoconfigure/AiGovernanceAutoConfigurationTest.java backend/mentor-api/src/main/java/org/congcong/algomentor/api/config/MentorAiConfiguration.java
git commit -m "feat: autoconfigure ai governance services"
```

### Task 10: Stable API Error Mapping

**Files:**
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/AiGovernanceExceptionHandler.java`
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/AgentConversationExceptionHandler.java`
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/learningplan/LearningPlanExceptionHandler.java`
- Create: `backend/mentor-api/src/test/java/org/congcong/algomentor/api/controller/AiGovernanceExceptionHandlerTest.java`

- [ ] **Step 1: Write failing handler tests**

Create test using a small controller that throws admission exceptions:

```java
@WebMvcTest(controllers = TestAiGovernanceController.class)
@Import(AiGovernanceExceptionHandler.class)
class AiGovernanceExceptionHandlerTest {

  @Autowired MockMvc mockMvc;

  @Test
  void mapsQuotaExceededToStableApiResponse() throws Exception {
    mockMvc.perform(get("/test/ai-governance/quota"))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("AI_QUOTA_EXCEEDED"))
        .andExpect(jsonPath("$.error.message").value("今日 AI 使用次数已达上限，请明天再试。"));
  }

  @Test
  void mapsConcurrentRunConflictToStableApiResponse() throws Exception {
    mockMvc.perform(get("/test/ai-governance/concurrent"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("AI_CONCURRENT_RUN_CONFLICT"))
        .andExpect(jsonPath("$.error.message").value("已有一个 AI 任务正在运行，请等待完成后再试。"));
  }
}
```

- [ ] **Step 2: Run test to verify RED**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-api -am test -Dtest=AiGovernanceExceptionHandlerTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: FAIL because handler does not exist.

- [ ] **Step 3: Implement global handler for governance exceptions**

Use:

```java
@RestControllerAdvice
public class AiGovernanceExceptionHandler {

  @ExceptionHandler(AiRunAdmissionException.class)
  public ResponseEntity<ApiResponse<Void>> admission(AiRunAdmissionException exception) {
    return ResponseEntity.status(exception.suggestedStatus())
        .contentType(MediaType.APPLICATION_JSON)
        .body(ApiResponse.failure(exception.code().name(), exception.getMessage(), exception.metadata()));
  }
}
```

HTTP statuses:

```text
AI_UNAUTHENTICATED -> 401
AI_FORBIDDEN -> 403
AI_QUOTA_EXCEEDED -> 429
AI_CONCURRENT_RUN_CONFLICT -> 409
AI_REQUEST_TOO_LARGE -> 413
AI_PURPOSE_DISABLED -> 403
AI_PROVIDER_DISABLED -> 503
AI_TIMEOUT -> 504
AI_RATE_LIMITED -> 429
AI_PROVIDER_UNAVAILABLE -> 503
AI_STRUCTURED_OUTPUT_INVALID -> 502
AI_CANCELLED -> 400
AI_UNKNOWN -> 500
```

- [ ] **Step 4: Run test to verify GREEN**

Run the same Maven command.

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/AiGovernanceExceptionHandler.java backend/mentor-api/src/test/java/org/congcong/algomentor/api/controller/AiGovernanceExceptionHandlerTest.java
git commit -m "feat: map ai governance errors to api responses"
```

### Task 11: Authenticated Actor Resolution In API Layer

**Files:**
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/service/AiActorResolver.java`
- Create: `backend/mentor-api/src/test/java/org/congcong/algomentor/api/service/AiActorResolverTest.java`

- [ ] **Step 1: Write failing resolver tests**

```java
class AiActorResolverTest {

  @Test
  void resolvesActorFromCurrentUserProvider() {
    CurrentUserIdProvider provider = () -> Optional.of(new AuthenticatedUserPrincipal(
        7L, "a@example.com", "A", null, List.of(AuthRole.USER), AuthUserStatus.ACTIVE));
    AiActor actor = new AiActorResolver(provider).currentActor();

    assertThat(actor.userId()).isEqualTo(7L);
    assertThat(actor.roles()).containsExactly(AuthRole.USER);
    assertThat(actor.authenticated()).isTrue();
  }

  @Test
  void returnsAnonymousWhenCurrentUserMissing() {
    AiActor actor = new AiActorResolver(Optional::empty).currentActor();

    assertThat(actor.authenticated()).isFalse();
    assertThat(actor.userId()).isNull();
  }
}
```

- [ ] **Step 2: Run test to verify RED**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-api -am test -Dtest=AiActorResolverTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: FAIL because resolver does not exist.

- [ ] **Step 3: Implement resolver**

Implementation:

```java
@Service
public class AiActorResolver {
  private final CurrentUserIdProvider currentUserIdProvider;

  public AiActorResolver(CurrentUserIdProvider currentUserIdProvider) {
    this.currentUserIdProvider = currentUserIdProvider;
  }

  public AiActor currentActor() {
    return currentUserIdProvider.currentUser()
        .map(user -> new AiActor(user.userId(), Set.copyOf(user.roles()), true))
        .orElseGet(AiActor::anonymous);
  }
}
```

- [ ] **Step 4: Run test to verify GREEN**

Run the same Maven command.

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/mentor-api/src/main/java/org/congcong/algomentor/api/service/AiActorResolver.java backend/mentor-api/src/test/java/org/congcong/algomentor/api/service/AiActorResolverTest.java
git commit -m "feat: resolve ai actor from auth context"
```

### Task 12: Govern Topic Explanation / Problem Explanation SSE

**Files:**
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/service/AiExplanationService.java`
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/AiStreamController.java`
- Modify: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/ExplainTopicUseCase.java`
- Modify: `backend/mentor-api/src/test/java/org/congcong/algomentor/api/controller/AiStreamControllerTest.java`

- [ ] **Step 1: Write failing controller tests**

Extend `AiStreamControllerTest`:

```java
@Test
void streamExplanationCallsGovernanceBeforeStartingPublisher() throws Exception {
  governance.consumeResult = true;

  MvcResult result = mockMvc.perform(get("/api/ai/explanations/stream")
          .param("topic", "two pointers")
          .accept(MediaType.TEXT_EVENT_STREAM))
      .andExpect(request().asyncStarted())
      .andReturn();

  mockMvc.perform(asyncDispatch(result))
      .andExpect(status().isOk())
      .andExpect(content().string(containsString("event:agent_run_start")));

  assertThat(governance.lastContext.purpose()).isEqualTo(AiPurpose.PROBLEM_EXPLANATION);
  assertThat(governance.lastContext.source()).isEqualTo(AiRunSource.PROBLEM_DETAIL);
  assertThat(useCase.lastRequest.metadata())
      .containsEntry(AiGovernanceMetadataKeys.PURPOSE, "PROBLEM_EXPLANATION");
}

@Test
void streamExplanationReturnsQuotaErrorBeforePublisherStarts() throws Exception {
  governance.throwOnAdmit = new AiRunAdmissionException(
      AiGovernanceErrorCode.AI_QUOTA_EXCEEDED,
      AiRunStatus.REJECTED_QUOTA,
      "今日 AI 使用次数已达上限，请明天再试。",
      HttpStatus.TOO_MANY_REQUESTS,
      Map.of());

  mockMvc.perform(get("/api/ai/explanations/stream")
          .param("topic", "two pointers")
          .accept(MediaType.TEXT_EVENT_STREAM))
      .andExpect(status().isTooManyRequests())
      .andExpect(jsonPath("$.error.code").value("AI_QUOTA_EXCEEDED"));
}
```

- [ ] **Step 2: Run test to verify RED**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-api -am test -Dtest=AiStreamControllerTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: FAIL because service does not use governance.

- [ ] **Step 3: Update `ExplainTopicUseCase` to accept admission metadata**

Add overload:

```java
public Flow.Publisher<AgentStreamEvent> stream(String topic, Map<String, Object> governanceMetadata) {
  return agentLoopRunner.stream(toAgentRequest(LearningTopic.of(topic), governanceMetadata));
}
```

`toAgentRequest` merges existing metadata with governance metadata:

```java
Map<String, Object> metadata = new LinkedHashMap<>();
metadata.put(AgentRuntimeMetadataKeys.TITLE, topic.title());
metadata.put(AgentRuntimeMetadataKeys.TOPIC_TITLE, topic.title());
metadata.put(AgentRuntimeMetadataKeys.ADAPTER, MentorApplicationConstants.TOPIC_EXPLANATION);
metadata.putAll(governanceMetadata == null ? Map.of() : governanceMetadata);
```

- [ ] **Step 4: Update `AiExplanationService` admission flow**

Constructor dependencies:

```java
ExplainTopicUseCase explainTopicUseCase;
LlmStreamSseMapper sseMapper;
AiActorResolver actorResolver;
AiRunAdmissionService admissionService;
```

In `streamExplanation`:

```java
AiRunContext context = new AiRunContext(
    UUID.randomUUID().toString(),
    actorResolver.currentActor(),
    AiPurpose.PROBLEM_EXPLANATION,
    AiRunSource.PROBLEM_DETAIL,
    null,
    topic.getBytes(StandardCharsets.UTF_8).length,
    true,
    Map.of("topicCharCount", topic.length()),
    Instant.now());
AiRunAdmission admission = admissionService.admit(context);
Flow.Publisher<AgentStreamEvent> publisher = explainTopicUseCase.stream(topic, admission.metadata());
```

Do not create `SseEmitter` before admission succeeds.

- [ ] **Step 5: Run test to verify GREEN**

Run the same Maven command.

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/mentor-api/src/main/java/org/congcong/algomentor/api/service/AiExplanationService.java backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/AiStreamController.java backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/ExplainTopicUseCase.java backend/mentor-api/src/test/java/org/congcong/algomentor/api/controller/AiStreamControllerTest.java
git commit -m "feat: govern problem explanation ai stream"
```

### Task 13: Govern Agent Conversation And Remove Client-Trusted User ID

**Files:**
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/AgentConversationController.java`
- Modify: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/conversation/AgentConversationCommand.java`
- Modify: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/conversation/AgentConversationService.java`
- Modify: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/conversation/AgentConversationRunCoordinator.java`
- Modify: `backend/mentor-api/src/test/java/org/congcong/algomentor/api/controller/AgentConversationControllerTest.java`

- [ ] **Step 1: Write failing controller tests**

Modify existing request body to omit `userId`. Add regression:

```java
@Test
void ignoresClientUserIdAndUsesAuthenticatedActorForGovernanceAndPreparation() throws Exception {
  currentUser = principal(7L, AuthRole.USER);

  MvcResult result = mockMvc.perform(post("/api/agent/conversations/stream")
          .contentType(MediaType.APPLICATION_JSON)
          .accept(MediaType.TEXT_EVENT_STREAM)
          .header("Idempotency-Key", "idem-1")
          .content("{\"taskId\":1,\"userId\":999,\"message\":\"Explain two pointers.\"}"))
      .andExpect(request().asyncStarted())
      .andReturn();

  mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk());

  assertThat(conversationRepository.lastRequest.userId()).isEqualTo(7L);
  assertThat(governance.lastContext.actor().userId()).isEqualTo(7L);
  assertThat(governance.lastContext.purpose()).isEqualTo(AiPurpose.LEARNING_CHAT);
  assertThat(governance.lastContext.source()).isEqualTo(AiRunSource.LEARNING_CHAT);
}
```

Add rejection test:

```java
@Test
void returnsConcurrentGovernanceConflictBeforePreparingConversationRun() throws Exception {
  governance.throwOnAdmit = concurrentConflict();

  mockMvc.perform(post("/api/agent/conversations/stream")
          .contentType(MediaType.APPLICATION_JSON)
          .accept(MediaType.TEXT_EVENT_STREAM)
          .content("{\"taskId\":1,\"message\":\"Explain sliding window.\"}"))
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.error.code").value("AI_CONCURRENT_RUN_CONFLICT"));

  assertThat(conversationRepository.lastRequest).isNull();
}
```

- [ ] **Step 2: Run test to verify RED**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-api -am test -Dtest=AgentConversationControllerTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: FAIL because controller still accepts and uses body `userId` and governance is not called.

- [ ] **Step 3: Change request DTO and command**

`ConversationStreamRequest` becomes:

```java
public record ConversationStreamRequest(
    @Positive Long taskId,
    @NotBlank String message
) {
}
```

Add `@JsonIgnoreProperties(ignoreUnknown = true)` so older clients that still send `userId` do not break request parsing, while the server ignores that value for AI governance and run preparation:

```java
@JsonIgnoreProperties(ignoreUnknown = true)
```

`AgentConversationCommand` adds governance metadata:

```java
Long taskId,
Long userId,
String userMessage,
String idempotencyKey,
Map<String, Object> governanceMetadata
```

Validate `userId` must be positive and non-null after controller admission succeeds.

- [ ] **Step 4: Admission in controller before run coordinator**

Controller flow:

```java
AiActor actor = actorResolver.currentActor();
AiRunContext context = new AiRunContext(
    UUID.randomUUID().toString(),
    actor,
    AiPurpose.LEARNING_CHAT,
    AiRunSource.LEARNING_CHAT,
    effectiveKey,
    request.message().getBytes(StandardCharsets.UTF_8).length,
    true,
    Map.of("taskId", request.taskId()),
    Instant.now());
AiRunAdmission admission = admissionService.admit(context);
Flow.Publisher<AgentStreamEvent> publisher = runCoordinator.stream(new AgentConversationCommand(
    request.taskId(),
    actor.userId(),
    request.message(),
    effectiveKey,
    admission.metadata()));
```

- [ ] **Step 5: Merge governance metadata into prepared AgentRequest**

In `AgentConversationService.toConversationRun`, after existing metadata is built:

```java
metadata.putAll(command.governanceMetadata());
```

`AgentConversationRunCoordinator` keeps existing task-level lock logic. It no longer acquires user-level AI lock because `AiRunAdmissionService` already did.

- [ ] **Step 6: Run tests to verify GREEN**

Run the same Maven command.

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/AgentConversationController.java backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/conversation backend/mentor-api/src/test/java/org/congcong/algomentor/api/controller/AgentConversationControllerTest.java
git commit -m "feat: govern learning chat agent runs"
```

### Task 14: Govern Learning Plan Draft Generation

**Files:**
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/learningplan/LearningPlanController.java`
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/learningplan/LearningPlanExceptionHandler.java`
- Modify: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/LearningPlanDraftService.java`
- Modify: `backend/mentor-api/src/test/java/org/congcong/algomentor/api/controller/learningplan/LearningPlanControllerTest.java`

- [ ] **Step 1: Write failing tests**

Extend `LearningPlanControllerTest`:

```java
@Test
void createDraftUsesCurrentUserForGovernanceAndConsumesLearningPlanQuota() throws Exception {
  currentUser = principal(7L, AuthRole.USER);

  mockMvc.perform(post("/api/learning-plans/drafts")
          .contentType(MediaType.APPLICATION_JSON)
          .content("""
              {"intent":"INTERVIEW_SPRINT","goal":"Java backend interview","durationWeeks":4,
               "level":"INTERMEDIATE","weeklyHours":8}
              """))
      .andExpect(status().isOk());

  assertThat(governance.lastContext.actor().userId()).isEqualTo(7L);
  assertThat(governance.lastContext.purpose()).isEqualTo(AiPurpose.LEARNING_PLAN);
  assertThat(governance.lastContext.source()).isEqualTo(AiRunSource.LEARNING_PLAN_DRAFT);
  assertThat(draftService.lastGovernanceMetadata)
      .containsEntry(AiGovernanceMetadataKeys.PURPOSE, "LEARNING_PLAN");
}

@Test
void continueDraftReturnsQuotaExceededBeforeCallingDraftService() throws Exception {
  governance.throwOnAdmit = quotaExceeded();

  mockMvc.perform(post("/api/learning-plans/drafts/12/messages")
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"message\":\"I can study 8 hours per week.\"}"))
      .andExpect(status().isTooManyRequests())
      .andExpect(jsonPath("$.error.code").value("AI_QUOTA_EXCEEDED"));

  assertThat(draftService.continueCalls).isZero();
}
```

- [ ] **Step 2: Run test to verify RED**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-api -am test -Dtest=LearningPlanControllerTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: FAIL because learning plan controller does not call governance.

- [ ] **Step 3: Add admission in `LearningPlanController`**

For create draft:

```java
AiRunAdmission admission = admissionService.admit(new AiRunContext(
    UUID.randomUUID().toString(),
    actorResolver.currentActor(),
    AiPurpose.LEARNING_PLAN,
    AiRunSource.LEARNING_PLAN_DRAFT,
    null,
    objectMapper.writeValueAsBytes(request).length,
    false,
    Map.of("entry", "createDraft"),
    Instant.now()));
draftService.createDraft(userId, request.toCommand(), admission.metadata());
```

For continue draft:

```java
AiRunAdmission admission = admissionService.admit(new AiRunContext(
    UUID.randomUUID().toString(),
    actorResolver.currentActor(),
    AiPurpose.LEARNING_PLAN,
    AiRunSource.LEARNING_PLAN_DRAFT,
    null,
    request.message().getBytes(StandardCharsets.UTF_8).length,
    false,
    Map.of("draftId", draftId, "entry", "continueDraft"),
    Instant.now()));
draftService.continueDraft(userId, draftId, request.message(), admission.metadata());
```

Do not govern `confirmDraft`, `listPlans`, or `getPlan` because they are not AI runs.

- [ ] **Step 4: Update `LearningPlanDraftService`**

Add overloads:

```java
public LearningPlanDraftResult createDraft(long userId, LearningPlanDraftCommand command, Map<String, Object> governanceMetadata)
public LearningPlanDraftResult continueDraft(long userId, long draftId, String message, Map<String, Object> governanceMetadata)
```

Pass `governanceMetadata` into private `advance(draft, governanceMetadata)`. Inject `AiRunLifecycleService` into `LearningPlanDraftService` and add:

```java
private LearningPlanDraftResult advance(LearningPlanDraft draft, Map<String, Object> governanceMetadata) {
  AiRunAdmission admission = (AiRunAdmission) governanceMetadata.get(AiGovernanceMetadataKeys.ADMISSION);
  lifecycle.markRunning(admission, null, null);
  try {
    LearningPlanDraftResult result = persistAgentResult(draft, agentService.run(
        draft.command(),
        validator.missingRequiredFields(draft.command())));
    lifecycle.markCompleted(admission, AiUsage.zero(), null, null);
    return result;
  } catch (RuntimeException ex) {
    lifecycle.markFailed(admission, AiGovernanceErrorCode.AI_UNKNOWN, AiUsage.zero(), null, null);
    throw ex;
  }
}
```

Keep the existing persistence behavior by moving the current body of `advance` into `persistAgentResult`. Even though the current `LearningPlanAgentService` is deterministic, the product entry is an AI feature and must produce completed/failed governance status immediately after admission.

- [ ] **Step 5: Run tests to verify GREEN**

Run the same Maven command.

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/learningplan backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/LearningPlanDraftService.java backend/mentor-api/src/test/java/org/congcong/algomentor/api/controller/learningplan/LearningPlanControllerTest.java
git commit -m "feat: govern learning plan ai generation"
```

### Task 15: AI_DEBUG Admin-Only Path And Purpose Guardrails

**Files:**
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/AiStreamController.java`
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/service/AiExplanationService.java`
- Modify: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/policy/AiGovernanceProperties.java`
- Modify: `backend/mentor-api/src/test/java/org/congcong/algomentor/api/controller/AiStreamControllerTest.java`

- [ ] **Step 1: Write failing admin-only tests**

Keep `/api/ai/explanations/stream` user-accessible as `PROBLEM_EXPLANATION`. Add an admission policy test asserting `AI_DEBUG` source cannot be used by a normal user:

```java
@Test
void debugSourceRequiresAdminActor() {
  Fixture fixture = new Fixture();
  AiRunContext context = new AiRunContext(
      "debug-run",
      new AiActor(7L, Set.of(AuthRole.USER), true),
      AiPurpose.PROBLEM_EXPLANATION,
      AiRunSource.AI_DEBUG,
      null,
      10,
      true,
      Map.of(),
      Instant.now());

  AiRunAdmissionException ex = catchThrowableOfType(
      () -> fixture.service.admit(context),
      AiRunAdmissionException.class);

  assertThat(ex.code()).isEqualTo(AiGovernanceErrorCode.AI_FORBIDDEN);
}
```

- [ ] **Step 2: Run test to verify RED**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl ai-governance -am test -Dtest=AiRunAdmissionServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: FAIL until admission service enforces `AI_DEBUG` source guard.

- [ ] **Step 3: Enforce debug source guard**

In `AiRunAdmissionService`, after actor authentication:

```java
if (context.source() == AiRunSource.AI_DEBUG && !context.actor().admin()) {
  reject(context, AiRunStatus.REJECTED_FORBIDDEN, AiGovernanceErrorCode.AI_FORBIDDEN, policy);
}
```

This keeps `AI_DEBUG` source admin-only even when its purpose policy is otherwise user-accessible.

- [ ] **Step 4: Run test to verify GREEN**

Run the same Maven command.

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/admission/AiRunAdmissionService.java backend/ai-governance/src/test/java/org/congcong/algomentor/ai/governance/admission/AiRunAdmissionServiceTest.java
git commit -m "feat: restrict ai debug governance source"
```

### Task 16: Provider And Agent Error Mapping Into Governance Codes

**Files:**
- Create: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/admission/AiGovernanceErrorMapper.java`
- Create: `backend/ai-governance/src/test/java/org/congcong/algomentor/ai/governance/admission/AiGovernanceErrorMapperTest.java`
- Modify: `backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/metrics/AiRunGovernanceObserver.java`

- [ ] **Step 1: Write failing mapper tests**

```java
class AiGovernanceErrorMapperTest {

  @Test
  void mapsLlmRateLimitAndProviderUnavailable() {
    AiGovernanceErrorMapper mapper = new AiGovernanceErrorMapper();

    assertThat(mapper.from(new LlmException(LlmErrorCode.RATE_LIMITED, "rate limited")))
        .isEqualTo(AiGovernanceErrorCode.AI_RATE_LIMITED);
    assertThat(mapper.from(new LlmException(LlmErrorCode.PROVIDER_UNAVAILABLE, "down")))
        .isEqualTo(AiGovernanceErrorCode.AI_PROVIDER_UNAVAILABLE);
  }

  @Test
  void mapsAgentStructuredOutputAndCancellation() {
    AiGovernanceErrorMapper mapper = new AiGovernanceErrorMapper();

    assertThat(mapper.from(new AgentException(AgentErrorCode.STRUCTURED_OUTPUT_INVALID, "bad json")))
        .isEqualTo(AiGovernanceErrorCode.AI_STRUCTURED_OUTPUT_INVALID);
    assertThat(mapper.from(new AgentException(AgentErrorCode.CANCELLED, "cancelled")))
        .isEqualTo(AiGovernanceErrorCode.AI_CANCELLED);
  }
}
```

Use the existing `LlmErrorCode` enum values from `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/exception/LlmErrorCode.java`: `INVALID_REQUEST`、`UNSUPPORTED_CAPABILITY`、`AUTHENTICATION_FAILED`、`PERMISSION_DENIED`、`RATE_LIMITED`、`TIMEOUT`、`PROVIDER_UNAVAILABLE`、`CONTENT_FILTERED`、`TOOL_CALL_INVALID`、`RESPONSE_PARSE_FAILED`、`CANCELLED`、`UNKNOWN`.

- [ ] **Step 2: Run test to verify RED**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl ai-governance -am test -Dtest=AiGovernanceErrorMapperTest,AiRunGovernanceObserverTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: FAIL because mapper does not exist.

- [ ] **Step 3: Implement mapper**

Mapping rules:

```text
AgentErrorCode.CANCELLED -> AI_CANCELLED
AgentErrorCode.STRUCTURED_OUTPUT_INVALID -> AI_STRUCTURED_OUTPUT_INVALID
AgentErrorCode.LLM_STREAM_FAILED with cause LlmException -> map from LlmException
LlmErrorCode.TIMEOUT -> AI_TIMEOUT
LlmErrorCode.RATE_LIMITED -> AI_RATE_LIMITED
LlmErrorCode.PROVIDER_UNAVAILABLE -> AI_PROVIDER_UNAVAILABLE
LlmErrorCode.INVALID_REQUEST with provider missing metadata -> AI_PROVIDER_DISABLED when metadata says provider unconfigured
default -> AI_UNKNOWN
```

Do not include provider exception message in user-visible metadata; log only code/runId/purpose/source/provider/model.

- [ ] **Step 4: Use mapper in governance observer**

Replace hard-coded `AI_UNKNOWN` mapping in `onError` with `errorMapper.from(error)`. If mapped code is `AI_CANCELLED`, call `markCancelled`; otherwise call `markFailed`.

- [ ] **Step 5: Run tests to verify GREEN**

Run the same Maven command.

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/admission/AiGovernanceErrorMapper.java backend/ai-governance/src/main/java/org/congcong/algomentor/ai/governance/metrics/AiRunGovernanceObserver.java backend/ai-governance/src/test/java/org/congcong/algomentor/ai/governance/admission/AiGovernanceErrorMapperTest.java backend/ai-governance/src/test/java/org/congcong/algomentor/ai/governance/metrics/AiRunGovernanceObserverTest.java
git commit -m "feat: map ai runtime errors to governance codes"
```

### Task 17: Configuration Defaults And Documentation Index

**Files:**
- Modify: `backend/mentor-api/src/main/resources/application.yml`
- Modify: `backend/mentor-api/src/main/resources/application-local.yml`
- Modify: `.env.example`
- Modify: `docs/code-index.md`

- [ ] **Step 1: Add configuration documentation**

Add default config under `application.yml`:

```yaml
algo-mentor:
  ai-governance:
    enabled: true
    quota-zone: UTC
    active-run-ttl: 30m
    purposes:
      learning-plan:
        daily-request-limit: 50
        max-concurrent-runs-per-user: 1
        max-request-bytes: 32768
        max-output-tokens: 4096
        max-steps: 12
        streaming-allowed: false
        tools-allowed: true
        structured-output-required: true
        admin-only: false
        system-policy-version: learning-plan-p0
      problem-explanation:
        daily-request-limit: 50
        max-concurrent-runs-per-user: 1
        max-request-bytes: 32768
        max-output-tokens: 2048
        max-steps: 8
        streaming-allowed: true
        tools-allowed: true
        structured-output-required: false
        admin-only: false
        system-policy-version: problem-explanation-p0
      learning-chat:
        daily-request-limit: 50
        max-concurrent-runs-per-user: 1
        max-request-bytes: 16384
        max-output-tokens: 2048
        max-steps: 8
        streaming-allowed: true
        tools-allowed: true
        structured-output-required: false
        admin-only: false
        system-policy-version: learning-chat-p0
```

Use enum names for purpose keys in configuration to avoid relying on relaxed binding for `EnumMap<AiPurpose, ...>` keys:

```yaml
LEARNING_PLAN:
PROBLEM_EXPLANATION:
LEARNING_CHAT:
```

- [ ] **Step 2: Update `.env.example`**

Add comments only, no secrets:

```dotenv
# AI governance defaults are configured in application.yml.
# Override per environment with SPRING_APPLICATION_JSON or profile-specific config.
```

- [ ] **Step 3: Update `docs/code-index.md`**

Add bullet:

```markdown
- `backend/ai-governance`：AI 调用治理模块，提供强类型 run context、purpose policy、准入、每日配额、active run 锁适配、admission 持久化、trace 访问策略和 Micrometer observer。
```

Add migration note:

```markdown
- `backend/ai-governance/src/main/resources/db/migration/ai`：AI governance Flyway 迁移目录，当前使用 `V10__ai_governance_schema.sql`，与其他模块共享版本空间。
```

- [ ] **Step 4: Run config binding test**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl ai-governance,mentor-api -am test -Dtest=AiPurposePolicyResolverTest,AiGovernanceAutoConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/mentor-api/src/main/resources/application.yml backend/mentor-api/src/main/resources/application-local.yml .env.example docs/code-index.md
git commit -m "docs: document ai governance configuration"
```

### Task 18: End-To-End Verification

**Files:**
- All modified files.

- [ ] **Step 1: Run ai-governance module tests**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl ai-governance -am test
```

Expected: PASS.

- [ ] **Step 2: Run affected backend tests**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl agent-core,agent-persistence-postgres,ai-governance,mentor-application,mentor-api -am test
```

Expected: PASS.

- [ ] **Step 3: Run full backend test suite**

Run:

```bash
make backend-test
```

Expected: PASS.

- [ ] **Step 4: Inspect migration discovery and version uniqueness**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-api -am test -Dtest=FlywayMigrationResourceTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: PASS, with versions `1,2,3,4,5,6,7,8,9,10` unique across classpath migration resources.

- [ ] **Step 5: Inspect git status**

Run:

```bash
git status --short
git diff --stat
```

Expected: changes are limited to `ai-governance`, planned integration files, targeted agent-core redaction/lock improvements, configuration, tests, and docs.

---

## Acceptance Checklist

- [ ] All real-user AI entry points construct `AiRunContext` and call `AiRunAdmissionService` before starting LLM/Agent execution.
- [ ] Ordinary users cannot spoof AI governance `userId` through request body; `AiActor` is derived only from authentication context.
- [ ] One user shares 50 AI requests per UTC day across `LEARNING_PLAN`, `PROBLEM_EXPLANATION`, and `LEARNING_CHAT`.
- [ ] One user can have at most one active AI run across all AI purposes in the current single-instance process.
- [ ] Admission success writes `ai_run_admissions`; quota consumption persists in `ai_daily_usage`.
- [ ] Success, failure, and cancellation update admission status and release active run locks.
- [ ] Admin trace access policy allows only `ADMIN`; non-admin users cannot read full trace content.
- [ ] Trace redaction covers API key, Authorization, OAuth token, session/cookie, JWT, database password, and secret fields.
- [ ] Micrometer exposes AI run requests, duration, errors, tokens, active runs, rejections, tool calls, tool errors, and SSE cancellation metrics without high-cardinality or sensitive tags.
- [ ] `llm-core` and `agent-core` do not gain user, quota, role, or admin-permission semantics, except the bounded `InMemoryAgentRunLockManager` TTL cleanup fix.

## Self-Review Notes

- Spec coverage: The plan maps model, policy, admission order, PostgreSQL persistence, active run lock, trace governance, metrics, API error mapping, and business entry integration into dedicated tasks.
- Boundary check: `ai-governance` does not depend on `mentor-api`, `mentor-application`, or `llm-openai`; integration happens from API/application modules inward.
- Known implementation constraint: `LearningPlanAgentService` is currently deterministic and not a true LLM call. The plan still governs the entry because the product behavior is an AI feature, and Task 14 wires synchronous lifecycle status through the in-process `AiRunAdmission` metadata object.
