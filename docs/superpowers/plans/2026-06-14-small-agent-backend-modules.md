# Small Agent Backend Modules Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the small Maven module structure for a future complex AI Agent backend while keeping the current API application buildable.

**Architecture:** Keep `mentor-api` as the Spring Boot web boundary. Add pure Java modules for domain, LLM abstraction, OpenAI adapter, Agent core, and application use cases so dependencies point inward and provider-specific SDK code stays isolated.

**Tech Stack:** Java 17, Maven multi-module, Spring Boot 3.5.15, JUnit 5, AssertJ, openai-java.

---

### Task 1: Module Skeleton

**Files:**
- Modify: `backend/pom.xml`
- Create: `backend/domain/pom.xml`
- Create: `backend/llm-core/pom.xml`
- Create: `backend/llm-openai/pom.xml`
- Create: `backend/agent-core/pom.xml`
- Create: `backend/mentor-application/pom.xml`

- [x] Add modules in dependency order: `common`, `domain`, `llm-core`, `llm-openai`, `agent-core`, `mentor-application`, `mentor-api`.
- [x] Keep `openai-java` dependency only in `llm-openai`.
- [x] Make `mentor-api` depend on `mentor-application` and `llm-openai`.

### Task 2: LLM Contract

**Files:**
- Create: `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmClient.java`
- Create: `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmMessage.java`
- Create: `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmRequest.java`
- Create: `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmResponse.java`
- Create: `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmStreamHandler.java`
- Test: `backend/llm-core/src/test/java/org/congcong/algomentor/llm/core/LlmRequestTest.java`

- [x] Write tests for request construction and message validation.
- [x] Implement minimal immutable records for request/response/message contracts.

### Task 3: OpenAI Adapter Boundary

**Files:**
- Create: `backend/llm-openai/src/main/java/org/congcong/algomentor/llm/openai/OpenAiLlmClient.java`
- Create: `backend/llm-openai/src/main/java/org/congcong/algomentor/llm/openai/OpenAiLlmProperties.java`
- Test: `backend/llm-openai/src/test/java/org/congcong/algomentor/llm/openai/OpenAiLlmPropertiesTest.java`

- [x] Write tests for default provider configuration.
- [x] Add a minimal adapter class that implements `LlmClient`; it can return an explicit disabled-provider error until real SDK wiring is implemented.

### Task 4: Agent And Application Boundary

**Files:**
- Create: `backend/domain/src/main/java/org/congcong/algomentor/domain/learning/LearningTopic.java`
- Create: `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/AgentRunner.java`
- Create: `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/AgentRequest.java`
- Create: `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/AgentResponse.java`
- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/ExplainTopicUseCase.java`
- Test: matching unit tests in each module.

- [x] Write tests showing `AgentRunner` calls only `LlmClient`.
- [x] Implement the smallest synchronous use case to prove module dependency direction.

### Task 5: Verification

**Files:**
- Modify: `docs/code-index.md`

- [x] Update the code index with the new modules.
- [x] Run `make backend-test`.
- [x] Fix compilation or test failures.
