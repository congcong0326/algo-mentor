# Learning Plan Agent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现学习计划第一版闭环：创建草案、追问、生成阶段级草案、确认保存、列表和详情。

**Architecture:** 后端在 `mentor-application` 放学习计划业务编排与端口，在 `mentor-api` 放 HTTP、MyBatis 持久化、Flyway 迁移和题库事实适配。第一版不让模型直接保存正式计划；Agent 服务用可测试的结构化结果生成器封装，后续可替换为真实 LLM/Agent loop。前端在现有单页中新增“学习计划”视图，复用 `services/api.ts` 和 `types/api.ts`。

**Tech Stack:** Java 17, Spring MVC, Maven, MyBatis, PostgreSQL/Flyway, React 19, TypeScript, Vite, Vitest/RTL.

---

## Files

- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/*`
- Create: `backend/mentor-application/src/test/java/org/congcong/algomentor/mentor/application/learningplan/*`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/learningplan/*`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/learningplan/*`
- Create: `backend/mentor-api/src/main/resources/mapper/learningplan/LearningPlanMapper.xml`
- Create: `backend/mentor-api/src/main/resources/db/migration/V8__learning_plan_schema.sql`
- Create: `backend/mentor-api/src/test/java/org/congcong/algomentor/api/controller/learningplan/*`
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/config/ApiContractConstants.java`
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/config/MentorApiMyBatisConfiguration.java`
- Modify: `backend/mentor-api/src/test/java/org/congcong/algomentor/api/config/FlywayMigrationResourceTest.java`
- Modify: `backend/mentor-api/src/test/java/org/congcong/algomentor/api/problem/mapper/ProblemMapperXmlTest.java`
- Create: `frontend/src/LearningPlans.tsx`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/services/api.ts`
- Modify: `frontend/src/types/api.ts`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/styles.css`

## Task 1: Application Learning Plan Domain

- [x] **Step 1: Write failing application tests**

Create `LearningPlanDraftServiceTest` covering missing facts, generated phases, idempotent confirm, and duration-based phase count.

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-application -am test -Dtest=LearningPlanDraftServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: compilation/test failure because learning plan classes do not exist.

- [x] **Step 2: Implement application models and services**

Create focused records/enums for commands, draft snapshots, plan snapshots, phase/problem snapshots, repositories, `LearningPlanAgentService`, `LearningPlanDraftValidator`, `LearningPlanDraftService`, and `LearningPlanService`.

- [x] **Step 3: Run application tests**

Run the same Maven command. Expected: PASS.

## Task 2: API Persistence and Controller

- [x] **Step 1: Write failing controller tests**

Create `LearningPlanControllerTest` using mocked service/provider dependencies. Cover create draft, continue message, confirm, list, detail, and unauthenticated access.

- [x] **Step 2: Write failing mapper XML resource test**

Extend mapper XML parsing coverage to include `mapper/learningplan/LearningPlanMapper.xml` statements. Expected: FAIL until XML exists.

- [x] **Step 3: Implement API constants, DTOs, controller, exception handler, repository, mapper, and Flyway migration**

Use `/api/learning-plans` constants. Store draft JSON payloads in JSONB, formal plans in normalized plan/phase/problem tables, and validate user ownership in repository queries.

- [x] **Step 4: Run API tests**

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-api -am test -Dtest=LearningPlanControllerTest,ProblemMapperXmlTest,FlywayMigrationResourceTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: PASS.

## Task 3: Frontend Learning Plan Workspace

- [x] **Step 1: Write failing frontend tests**

Extend `App.test.tsx` to cover learning plan tab, form submission, clarification, generated draft preview, confirm, and detail rendering.

Run:

```bash
npm --cache ./.npm --prefix frontend test -- App.test.tsx
```

Expected: FAIL until frontend is implemented. If `vitest` is missing, run `make frontend-install` first.

- [x] **Step 2: Implement frontend API types and functions**

Add learning plan DTOs to `frontend/src/types/api.ts` and functions to `frontend/src/services/api.ts`.

- [x] **Step 3: Implement `LearningPlans.tsx` and app navigation**

Add a dense workspace with list, creation form, assistant follow-up, draft preview, confirm action, and detail view.

- [x] **Step 4: Run frontend tests**

Run the same npm test command. Expected: PASS.

## Task 4: Final Verification

- [x] Run `make backend-test`. Expected: PASS.
- [x] Run `make frontend-test`. Expected: PASS.
- [x] Run `git status --short` and summarize changed files.
