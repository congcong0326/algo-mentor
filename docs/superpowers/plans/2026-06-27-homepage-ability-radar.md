# 主页能力雷达图 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 登录后默认进入 `/`，展示认证态工作台，并在右侧用 23 个常见 tag 的实时 Review 聚合结果渲染能力雷达图。

**Architecture:** 后端在 `mentor-api` 内新增 ability controller/service/mapper/model，API 只读取 `CurrentUserIdProvider` 的当前用户，不接受前端 userId。前端新增 ability 类型、API client 和 SVG 雷达图组件，并把认证态默认路由从学习计划页调整到主页工作台。

**Tech Stack:** Java 17、Spring MVC、MyBatis XML、PostgreSQL 数组展开、React 19、TypeScript、Vitest、React Testing Library、SVG。

---

### Task 1: Backend Ability Profile API

**Files:**
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/config/ApiContractConstants.java`
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/config/MentorApiMyBatisConfiguration.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/ability/AbilityProfileController.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/ability/AbilityProfileUnauthenticatedException.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/ability/model/AbilityProfileResponse.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/ability/model/AbilityProfileScopeResponse.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/ability/model/AbilityTagScoreResponse.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/ability/service/AbilityProfileConstants.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/ability/service/AbilityProfileService.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/ability/mapper/AbilityProfileMapper.java`
- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/ability/mapper/model/AbilityTagScoreRow.java`
- Create: `backend/mentor-api/src/main/resources/mapper/ability/AbilityProfileMapper.xml`
- Create: `backend/mentor-api/src/test/java/org/congcong/algomentor/api/ability/service/AbilityProfileServiceTest.java`
- Create: `backend/mentor-api/src/test/java/org/congcong/algomentor/api/ability/mapper/AbilityProfileMapperXmlTest.java`
- Create: `backend/mentor-api/src/test/java/org/congcong/algomentor/api/controller/ability/AbilityProfileControllerTest.java`

- [ ] **Step 1: Write failing service tests**

Add tests that mock `AbilityProfileMapper` and verify:
- no Review rows return tags with `0.0` `rawAverageScore` and `abilityScore`;
- 1 perfect Review returns `2.0`;
- 3 reviews averaging `8.0` returns `3.4`;
- 8 reviews averaging `8.0` returns `5.3`;
- response scope is `minProblemCount=20`, `scorePrecision=1`, `latestReviewOnly=true`, `conservativeWeight=4`.

Run:
`mvn -f backend/pom.xml -pl mentor-api -Dtest=AbilityProfileServiceTest -Dmaven.repo.local=../.m2/repository test`

Expected: FAIL because ability service classes do not exist.

- [ ] **Step 2: Write failing mapper XML tests**

Add a MyBatis XML parse test for `mapper/ability/AbilityProfileMapper.xml` and SQL-shape assertions that the query:
- uses `ROW_NUMBER() OVER (PARTITION BY problem_slug ORDER BY created_at DESC, id DESC)` or equivalent latest-review ranking;
- expands problem tags with `unnest`;
- left joins tag catalog to review aggregates so empty tags remain in the result;
- filters catalog tags by `HAVING COUNT(*) >= #{minProblemCount}`;
- orders by `problem_count DESC, tag ASC`.

Run:
`mvn -f backend/pom.xml -pl mentor-api -Dtest=AbilityProfileMapperXmlTest -Dmaven.repo.local=../.m2/repository test`

Expected: FAIL because mapper XML does not exist.

- [ ] **Step 3: Write failing controller tests**

Add `@WebMvcTest(AbilityProfileController.class)` tests that verify:
- `GET /api/abilities/profile` returns current user data and never reads a request `userId`;
- unauthenticated access returns 401 with `AUTH_UNAUTHENTICATED`;
- the successful response wraps the ability profile in `ApiResponse`.

Run:
`mvn -f backend/pom.xml -pl mentor-api -Dtest=AbilityProfileControllerTest -Dmaven.repo.local=../.m2/repository test`

Expected: FAIL because the controller does not exist.

- [ ] **Step 4: Implement backend API**

Implement constants, DTO records, mapper interface/row, MyBatis SQL, service rounding logic, controller, unauthenticated exception, and MyBatis bean registration. Use `BigDecimal` with one-decimal `HALF_UP` rounding. Keep path and algorithm constants in `AbilityProfileConstants` and `ApiContractConstants`.

- [ ] **Step 5: Verify backend tests**

Run:
`mvn -f backend/pom.xml -pl mentor-api -Dtest=AbilityProfileServiceTest,AbilityProfileMapperXmlTest,AbilityProfileControllerTest -Dmaven.repo.local=../.m2/repository test`

Expected: PASS.

### Task 2: Frontend Ability Dashboard

**Files:**
- Modify: `frontend/src/types/api.ts`
- Modify: `frontend/src/services/api.ts`
- Modify: `frontend/src/services/api.test.ts`
- Modify: `frontend/src/app/navigation.ts`
- Modify: `frontend/src/app/AppShell.test.tsx`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/HomeDashboard.tsx`
- Modify: `frontend/src/i18n/locales.ts`
- Modify: `frontend/src/styles.css`
- Create: `frontend/src/ability/AbilityRadarChart.tsx`
- Create: `frontend/src/ability/AbilityRadarChart.test.tsx`

- [ ] **Step 1: Write failing API client test**

Add `getAbilityProfile` test asserting it calls `GET /api/abilities/profile` with JSON Accept and locale headers.

Run:
`npm --cache ./.npm --prefix frontend test -- --run frontend/src/services/api.test.ts`

Expected: FAIL because `getAbilityProfile` does not exist.

- [ ] **Step 2: Write failing radar component tests**

Add tests for `AbilityRadarChart` that render 23 tags, show one-decimal scores including `0.0`, include tick labels `2/4/6/8/10`, and do not render buttons, links, or tooltip attributes.

Run:
`npm --cache ./.npm --prefix frontend test -- --run frontend/src/ability/AbilityRadarChart.test.tsx`

Expected: FAIL because component does not exist.

- [ ] **Step 3: Write failing authenticated home route tests**

Update app tests so authenticated `/` and successful password login land on `/`, show `能力雷达图`, and fetch `/api/abilities/profile`. Update shell tests so authenticated navigation includes 首页.

Run:
`npm --cache ./.npm --prefix frontend test -- --run frontend/src/App.test.tsx frontend/src/app/AppShell.test.tsx`

Expected: FAIL because authenticated `/` still redirects to `/learning-plans`.

- [ ] **Step 4: Implement frontend ability dashboard**

Add TypeScript ability contracts and `getAbilityProfile`. Add `AbilityRadarChart` as a lightweight SVG with static tick labels, no click handlers, no links, no tooltip attributes. Refactor `HomeDashboard` to render the existing public marketing home when unauthenticated props are provided and a dense authenticated workbench when used inside `AppShell`; workbench right side loads ability profile with loading, error retry, and normal empty Review radar states.

- [ ] **Step 5: Verify frontend tests**

Run:
`npm --cache ./.npm --prefix frontend test -- --run frontend/src/services/api.test.ts frontend/src/ability/AbilityRadarChart.test.tsx frontend/src/App.test.tsx frontend/src/app/AppShell.test.tsx`

Expected: PASS.

### Task 3: Final Verification

**Files:**
- Review all files touched by Tasks 1 and 2.

- [ ] **Step 1: Run focused backend verification**

Run:
`mvn -f backend/pom.xml -pl mentor-api -Dtest=AbilityProfileServiceTest,AbilityProfileMapperXmlTest,AbilityProfileControllerTest -Dmaven.repo.local=../.m2/repository test`

Expected: PASS.

- [ ] **Step 2: Run focused frontend verification**

Run:
`npm --cache ./.npm --prefix frontend test -- --run frontend/src/services/api.test.ts frontend/src/ability/AbilityRadarChart.test.tsx frontend/src/App.test.tsx frontend/src/app/AppShell.test.tsx`

Expected: PASS.

- [ ] **Step 3: Review diff**

Run:
`git diff -- backend/mentor-api frontend/src docs/superpowers/plans/2026-06-27-homepage-ability-radar.md`

Expected: only ability API, authenticated dashboard/radar, tests, and plan changes are present; unrelated existing modified files remain untouched.
