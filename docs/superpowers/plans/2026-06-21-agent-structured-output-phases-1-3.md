# Agent 结构化输出阶段一到三实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完成 `docs/agent-structured-output-design.md` 中阶段一、阶段二、阶段三，让 Agent run 显式携带执行配置、捕获最终输出，并让持久化 observer 只保存最终 assistant message。

**Architecture:** `agent-core` 新增强类型执行配置和最终输出模型，`AgentLoopRunner` 在最终 step 后构造 `AgentOutput` 并通过 lifecycle 通知 observer。`agent-persistence-postgres` 改为在 `onFinalOutput` 保存 assistant message，`onRunEnd` 只更新 run/turn 成功状态。

**Tech Stack:** Java 17、Maven、Jackson、JUnit 5、AssertJ。

---

### Task 1: 执行配置入 core

**Files:**
- Create: `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/AgentExecutionOptions.java`
- Create: `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/AgentStructuredOutputOptions.java`
- Create: `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/StructuredOutputStrategy.java`
- Modify: `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/AgentRequest.java`
- Modify: `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/AgentLlmRequestFactory.java`
- Test: `backend/agent-core/src/test/java/org/congcong/algomentor/agent/core/AgentRequestTest.java`
- Test: `backend/agent-core/src/test/java/org/congcong/algomentor/agent/core/AgentLlmRequestFactoryTest.java`

- [ ] **Step 1: Write failing tests**

验证旧构造函数默认 text/default options，验证带 `JsonSchema` 的 execution options 透传到 `LlmCompletionRequest.options()` 与 `responseFormat()`。

- [ ] **Step 2: Run red tests**

Run: `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl agent-core -am -Dtest=AgentRequestTest,AgentLlmRequestFactoryTest -Dsurefire.failIfNoSpecifiedTests=false test`

- [ ] **Step 3: Implement minimal code**

新增三个配置模型；`AgentRequest` 增加 `executionOptions` 字段并保留旧构造；`AgentLlmRequestFactory` 从 `AgentRequest.executionOptions()` 写入 builder。

- [ ] **Step 4: Run green tests**

Run: `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl agent-core -am -Dtest=AgentRequestTest,AgentLlmRequestFactoryTest -Dsurefire.failIfNoSpecifiedTests=false test`

### Task 2: 最终输出捕获

**Files:**
- Create: `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/AgentOutput.java`
- Modify: `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/AgentStepResult.java`
- Modify: `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/AgentRunResult.java`
- Modify: `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/AgentLoopObserver.java`
- Modify: `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/AgentLoopLifecycle.java`
- Modify: `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/AgentLoopRunner.java`
- Modify: `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/AgentErrorCode.java`
- Test: `backend/agent-core/src/test/java/org/congcong/algomentor/agent/core/AgentLoopRunnerTest.java`

- [ ] **Step 1: Write failing tests**

覆盖文本最终输出、JSON schema 解析、required JSON 非法时报 `STRUCTURED_OUTPUT_INVALID`、工具调用 step 内容不成为 final output、`onFinalOutput` 在 `onRunEnd` 前触发。

- [ ] **Step 2: Run red tests**

Run: `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl agent-core -am -Dtest=AgentLoopRunnerTest -Dsurefire.failIfNoSpecifiedTests=false test`

- [ ] **Step 3: Implement minimal code**

`StepCollector` 聚合 `ContentDelta`；`AgentStepResult` 增加 `content`；`AgentLoopRunner` 在 final step 构造 `AgentOutput`，解析 JSON response format，并依次触发 `finalOutput` 和 `runEnded`。

- [ ] **Step 4: Run green tests**

Run: `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl agent-core -am -Dtest=AgentLoopRunnerTest -Dsurefire.failIfNoSpecifiedTests=false test`

### Task 3: 持久化 observer 调整

**Files:**
- Modify: `backend/agent-persistence-postgres/src/main/java/org/congcong/algomentor/agent/persistence/postgres/observer/PersistentAgentRunObserver.java`
- Modify: `backend/agent-persistence-postgres/src/test/java/org/congcong/algomentor/agent/persistence/postgres/observer/PersistentAgentRunObserverTest.java`

- [ ] **Step 1: Write failing tests**

覆盖 `onFinalOutput` 插入 assistant message；覆盖多 step token 中只有 final output 被保存；覆盖 `onRunEnd` 不再根据 token buffer 插入 assistant message。

- [ ] **Step 2: Run red tests**

Run: `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl agent-persistence-postgres -am -Dtest=PersistentAgentRunObserverTest -Dsurefire.failIfNoSpecifiedTests=false test`

- [ ] **Step 3: Implement minimal code**

`PersistentAgentRunObserver` 保留 provider/model/usage buffer，新增 final assistant message id buffer；`onFinalOutput` 保存 assistant message；`onRunEnd` 只更新 run succeeded 和 turn succeeded。

- [ ] **Step 4: Run green tests**

Run: `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl agent-persistence-postgres -am -Dtest=PersistentAgentRunObserverTest -Dsurefire.failIfNoSpecifiedTests=false test`

### Task 4: 回归验证

**Files:**
- Verify all changed backend modules.

- [ ] **Step 1: Run related module tests**

Run: `mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl agent-core,agent-persistence-postgres -am test`

- [ ] **Step 2: Inspect diff**

Run: `git diff -- backend/agent-core backend/agent-persistence-postgres docs/superpowers/plans/2026-06-21-agent-structured-output-phases-1-3.md`
