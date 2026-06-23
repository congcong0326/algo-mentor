# Learning Plan Page Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the redesigned learning plan management page with a summary/create card, paginated deletable plan list, modal questionnaire, and page-level draft preview.

**Architecture:** Start by changing the backend contract from full-list arrays to a paginated page object with aggregate counts and a delete use case. Then update frontend API types and split the learning plan UI into focused components: summary card, list card, create modal, and difficulty control. Keep draft generation on the existing draft API and reuse the existing draft preview/confirmation flow.

**Tech Stack:** Java 17, Spring MVC, Maven, MyBatis, PostgreSQL, React 19, TypeScript, Vite, Vitest, React Testing Library, lucide-react.

---

## Source Spec

Implement the approved design in:

`docs/superpowers/specs/2026-06-23-learning-plan-page-optimization-design.md`

Key decisions from the spec:

- No search box.
- Top card shows aggregate stats and one `新建计划` button.
- Plan list is backend-paginated.
- Plan rows show content left, status/actions right.
- Delete is a real backend operation.
- Create flow uses a modal questionnaire, not a wizard.
- No standalone user-entered `学习目标` field in the modal.
- Model-generated `draftPlan.goal` is the editable target summary in draft preview.

## File Map

Backend application layer:

- Modify `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/LearningPlanRepository.java`
  - Add pagination, aggregate count, draft reference clearing, and delete methods.
- Create `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/LearningPlanPage.java`
  - Application-layer page result with `items`, `total`, `page`, `pageSize`, `activeCount`, `archivedCount`, `latestCreatedAt`.
- Modify `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/LearningPlanService.java`
  - Add normalized paginated list use case and delete use case.
- Create `backend/mentor-application/src/test/java/org/congcong/algomentor/mentor/application/learningplan/LearningPlanServiceTest.java`
  - Unit tests for pagination normalization and delete behavior.

Backend API and persistence:

- Modify `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/learningplan/LearningPlanController.java`
  - Change list endpoint to accept `page/pageSize` and return page response.
  - Add delete endpoint.
- Create `backend/mentor-api/src/main/java/org/congcong/algomentor/api/learningplan/model/LearningPlanPageResponse.java`
  - API DTO for paginated plan list.
- Modify `backend/mentor-api/src/main/java/org/congcong/algomentor/api/learningplan/model/LearningPlanResponseMapper.java`
  - Map application page to API page.
- Modify `backend/mentor-api/src/main/java/org/congcong/algomentor/api/learningplan/mapper/LearningPlanMapper.java`
  - Add mapper methods for page query, counts, latest timestamp, draft reference clearing, delete.
- Modify `backend/mentor-api/src/main/resources/mapper/learningplan/LearningPlanMapper.xml`
  - Add SQL for the new mapper methods.
- Modify `backend/mentor-api/src/main/java/org/congcong/algomentor/api/learningplan/repository/MyBatisLearningPlanRepository.java`
  - Implement new repository methods.
- Modify `backend/mentor-api/src/main/java/org/congcong/algomentor/api/learningplan/repository/UnavailableLearningPlanRepository.java`
  - Implement new methods by throwing `LearningPlanRepositoryUnavailableException`.
- Modify `backend/mentor-api/src/test/java/org/congcong/algomentor/api/controller/learningplan/LearningPlanControllerTest.java`
  - Update list assertions and add delete test.

Frontend contracts and options:

- Modify `frontend/src/types/api.ts`
  - Add `LearningPlanPageResponse`, `LearningPlanListQuery`, `DifficultyDistributionLevel`.
- Modify `frontend/src/services/api.ts`
  - Update `getLearningPlans(query)` and add `deleteLearningPlan(planId)`.
- Modify `frontend/src/learning-plans/options.ts`
  - Add scenario, language, topic, and difficulty distribution options.
- Add focused unit tests under `frontend/src/learning-plans`.

Frontend UI:

- Create `frontend/src/learning-plans/DifficultyDistributionControl.tsx`
- Create `frontend/src/learning-plans/LearningPlanCreateModal.tsx`
- Create `frontend/src/learning-plans/LearningPlanSummaryCard.tsx`
- Create `frontend/src/learning-plans/LearningPlanListCard.tsx`
- Modify `frontend/src/LearningPlans.tsx`
  - Replace wizard/sidebar layout with summary card, list card, modal, page-level draft/detail area.
- Modify `frontend/src/learning-plans/LearningPlanDraftPanel.tsx`
  - Add editable goal summary and regenerate action.
- Modify `frontend/src/styles.css`
  - Add modal, summary card, list card, pagination, responsive layout styles.
- Modify `frontend/src/App.test.tsx`
  - Update integration tests for the new page.
- Retire `frontend/src/learning-plans/LearningPlanWizard.tsx` and `LearningPlanWizard.test.tsx` after the modal tests cover replacement behavior.

---

## Task 1: Backend Application Pagination And Delete Use Cases

**Files:**

- Create: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/LearningPlanPage.java`
- Modify: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/LearningPlanRepository.java`
- Modify: `backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/LearningPlanService.java`
- Create: `backend/mentor-application/src/test/java/org/congcong/algomentor/mentor/application/learningplan/LearningPlanServiceTest.java`

- [ ] **Step 1: Write the failing service tests**

Create `LearningPlanServiceTest.java` with these tests:

```java
package org.congcong.algomentor.mentor.application.learningplan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LearningPlanServiceTest {

  @Test
  void listPlansNormalizesPageAndPageSize() {
    FakeLearningPlanRepository repository = new FakeLearningPlanRepository();
    repository.storedPage = new LearningPlanPage(List.of(plan(1L, LearningPlanStatus.ACTIVE)), 1, 1, 50, 1, 0, Instant.parse("2026-06-22T00:00:00Z"));

    LearningPlanPage page = new LearningPlanService(repository).listPlans(42L, 0, 500);

    assertThat(page.page()).isEqualTo(1);
    assertThat(page.pageSize()).isEqualTo(50);
    assertThat(repository.lastPage).isEqualTo(1);
    assertThat(repository.lastPageSize).isEqualTo(50);
  }

  @Test
  void deletePlanClearsDraftReferenceAndDeletesOwnedPlan() {
    FakeLearningPlanRepository repository = new FakeLearningPlanRepository();
    repository.plans.add(plan(900L, LearningPlanStatus.ACTIVE));

    new LearningPlanService(repository).deletePlan(42L, 900L);

    assertThat(repository.clearedPlanIds).containsExactly(900L);
    assertThat(repository.deletedPlanIds).containsExactly(900L);
  }

  @Test
  void deletePlanThrowsWhenPlanIsMissing() {
    FakeLearningPlanRepository repository = new FakeLearningPlanRepository();

    assertThatThrownBy(() -> new LearningPlanService(repository).deletePlan(42L, 900L))
        .isInstanceOf(LearningPlanException.class)
        .hasMessage("学习计划不存在。");

    assertThat(repository.clearedPlanIds).isEmpty();
    assertThat(repository.deletedPlanIds).isEmpty();
  }

  private static LearningPlan plan(long id, LearningPlanStatus status) {
    return new LearningPlan(
        id,
        42L,
        status,
        new LearningPlanDraftPlan(
            "四周 Java 算法面试冲刺计划",
            "摘要",
            LearningPlanIntent.INTERVIEW_SPRINT,
            "准备 Java 后端算法面试",
            4,
            LearningPlanLevel.INTERMEDIATE,
            6,
            "Java",
            LearningPlanDifficultyPreference.MEDIUM,
            true,
            List.of("Array"),
            "中级，每周 6 小时。",
            List.of(),
            Map.of()),
        Instant.parse("2026-06-22T00:00:00Z"),
        Instant.parse("2026-06-22T00:00:00Z"));
  }

  private static class FakeLearningPlanRepository implements LearningPlanRepository {
    final List<LearningPlan> plans = new ArrayList<>();
    final List<Long> clearedPlanIds = new ArrayList<>();
    final List<Long> deletedPlanIds = new ArrayList<>();
    LearningPlanPage storedPage = new LearningPlanPage(List.of(), 0, 1, 10, 0, 0, null);
    int lastPage;
    int lastPageSize;

    @Override
    public LearningPlan save(LearningPlan plan) {
      return plan;
    }

    @Override
    public List<LearningPlan> findByUserId(long userId) {
      return plans;
    }

    @Override
    public LearningPlanPage findPageByUserId(long userId, int page, int pageSize) {
      lastPage = page;
      lastPageSize = pageSize;
      return new LearningPlanPage(
          storedPage.items(),
          storedPage.total(),
          page,
          pageSize,
          storedPage.activeCount(),
          storedPage.archivedCount(),
          storedPage.latestCreatedAt());
    }

    @Override
    public Optional<LearningPlan> findPlanByIdForUser(long planId, long userId) {
      return plans.stream().filter(plan -> plan.id() == planId).findFirst();
    }

    @Override
    public void clearConfirmedPlanReferences(long userId, long planId) {
      clearedPlanIds.add(planId);
    }

    @Override
    public boolean deletePlanByIdForUser(long planId, long userId) {
      boolean removed = plans.removeIf(plan -> plan.id() == planId);
      if (removed) {
        deletedPlanIds.add(planId);
      }
      return removed;
    }
  }
}
```

- [ ] **Step 2: Run the failing service tests**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-application -Dtest=LearningPlanServiceTest test
```

Expected: compilation fails because `LearningPlanPage`, `findPageByUserId`, `clearConfirmedPlanReferences`, `deletePlanByIdForUser`, and new service methods do not exist.

- [ ] **Step 3: Add `LearningPlanPage`**

Create `LearningPlanPage.java`:

```java
package org.congcong.algomentor.mentor.application.learningplan;

import java.time.Instant;
import java.util.List;

public record LearningPlanPage(
    List<LearningPlan> items,
    long total,
    int page,
    int pageSize,
    long activeCount,
    long archivedCount,
    Instant latestCreatedAt) {

  public LearningPlanPage {
    items = List.copyOf(items);
  }
}
```

- [ ] **Step 4: Extend `LearningPlanRepository`**

Modify `LearningPlanRepository.java` to include:

```java
LearningPlanPage findPageByUserId(long userId, int page, int pageSize);

void clearConfirmedPlanReferences(long userId, long planId);

boolean deletePlanByIdForUser(long planId, long userId);
```

Keep the existing `findByUserId(long userId)` until all existing callers are migrated.

- [ ] **Step 5: Implement service methods**

Modify `LearningPlanService.java`:

```java
private static final int DEFAULT_PAGE = 1;
private static final int DEFAULT_PAGE_SIZE = 10;
private static final int MAX_PAGE_SIZE = 50;

public LearningPlanPage listPlans(long userId, Integer page, Integer pageSize) {
  int normalizedPage = page == null || page < 1 ? DEFAULT_PAGE : page;
  int normalizedPageSize = pageSize == null || pageSize < 1
      ? DEFAULT_PAGE_SIZE
      : Math.min(pageSize, MAX_PAGE_SIZE);
  return planRepository.findPageByUserId(userId, normalizedPage, normalizedPageSize);
}

public void deletePlan(long userId, long planId) {
  getPlan(userId, planId);
  planRepository.clearConfirmedPlanReferences(userId, planId);
  boolean deleted = planRepository.deletePlanByIdForUser(planId, userId);
  if (!deleted) {
    throw new LearningPlanException("LEARNING_PLAN_NOT_FOUND", "学习计划不存在。");
  }
}
```

- [ ] **Step 6: Run service tests**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-application -Dtest=LearningPlanServiceTest test
```

Expected: `LearningPlanServiceTest` passes.

- [ ] **Step 7: Commit**

```bash
git add backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/LearningPlanPage.java \
  backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/LearningPlanRepository.java \
  backend/mentor-application/src/main/java/org/congcong/algomentor/mentor/application/learningplan/LearningPlanService.java \
  backend/mentor-application/src/test/java/org/congcong/algomentor/mentor/application/learningplan/LearningPlanServiceTest.java
git commit -m "feat: add learning plan page use cases"
```

---

## Task 2: Backend API And MyBatis Pagination/Delete Contract

**Files:**

- Create: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/learningplan/model/LearningPlanPageResponse.java`
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/learningplan/model/LearningPlanResponseMapper.java`
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller/learningplan/LearningPlanController.java`
- Modify: `backend/mentor-api/src/test/java/org/congcong/algomentor/api/controller/learningplan/LearningPlanControllerTest.java`
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/learningplan/mapper/LearningPlanMapper.java`
- Modify: `backend/mentor-api/src/main/resources/mapper/learningplan/LearningPlanMapper.xml`
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/learningplan/repository/MyBatisLearningPlanRepository.java`
- Modify: `backend/mentor-api/src/main/java/org/congcong/algomentor/api/learningplan/repository/UnavailableLearningPlanRepository.java`

- [ ] **Step 1: Update controller tests first**

Modify `LearningPlanControllerTest.listAndDetailUseCurrentUser` to expect a page object:

```java
when(planService.listPlans(42L, 2, 5)).thenReturn(new LearningPlanPage(
    List.of(plan),
    12,
    2,
    5,
    8,
    4,
    Instant.parse("2026-06-22T00:00:00Z")));

mockMvc.perform(get("/api/learning-plans?page=2&pageSize=5"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.data.items[0].id").value(900))
    .andExpect(jsonPath("$.data.items[0].title").value("四周 Java 算法面试冲刺计划"))
    .andExpect(jsonPath("$.data.total").value(12))
    .andExpect(jsonPath("$.data.page").value(2))
    .andExpect(jsonPath("$.data.pageSize").value(5))
    .andExpect(jsonPath("$.data.activeCount").value(8))
    .andExpect(jsonPath("$.data.archivedCount").value(4))
    .andExpect(jsonPath("$.data.latestCreatedAt").value("2026-06-22T00:00:00Z"));
```

Add a delete test:

```java
@Test
void deletePlanUsesCurrentUser() throws Exception {
  when(currentUserIdProvider.currentUser()).thenReturn(Optional.of(currentUser()));

  mockMvc.perform(delete("/api/learning-plans/900"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.success").value(true));

  verify(planService).deletePlan(42L, 900L);
}
```

Add the missing static import:

```java
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
```

- [ ] **Step 2: Run controller tests and verify failure**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-api -Dtest=LearningPlanControllerTest test
```

Expected: compilation fails because controller and response DTO do not support the new page and delete API.

- [ ] **Step 3: Add page response DTO**

Create `LearningPlanPageResponse.java`:

```java
package org.congcong.algomentor.api.learningplan.model;

import java.time.Instant;
import java.util.List;

public record LearningPlanPageResponse(
    List<LearningPlanSummaryResponse> items,
    long total,
    int page,
    int pageSize,
    long activeCount,
    long archivedCount,
    Instant latestCreatedAt) {
}
```

- [ ] **Step 4: Add mapper response method**

In `LearningPlanResponseMapper.java`, add:

```java
public static LearningPlanPageResponse toPageResponse(LearningPlanPage page) {
  return new LearningPlanPageResponse(
      page.items().stream().map(LearningPlanResponseMapper::toSummaryResponse).toList(),
      page.total(),
      page.page(),
      page.pageSize(),
      page.activeCount(),
      page.archivedCount(),
      page.latestCreatedAt());
}
```

Add import:

```java
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanPage;
```

- [ ] **Step 5: Update controller endpoint**

Modify `LearningPlanController.java` imports:

```java
import org.congcong.algomentor.api.learningplan.model.LearningPlanPageResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;
```

Replace list method:

```java
@GetMapping
public ApiResponse<LearningPlanPageResponse> listPlans(
    @RequestParam(required = false) Integer page,
    @RequestParam(required = false) Integer pageSize) {
  long userId = requireCurrentUserId();
  return ApiResponse.success(LearningPlanResponseMapper.toPageResponse(planService.listPlans(userId, page, pageSize)));
}
```

Add delete method:

```java
@DeleteMapping("/{planId}")
public ApiResponse<Void> deletePlan(@PathVariable long planId) {
  long userId = requireCurrentUserId();
  planService.deletePlan(userId, planId);
  return ApiResponse.success(null);
}
```

- [ ] **Step 6: Extend MyBatis mapper interface**

Add to `LearningPlanMapper.java`:

```java
List<LearningPlanRow> findPlansByUserIdPage(
    @Param("userId") long userId,
    @Param("limit") int limit,
    @Param("offset") int offset);

long countPlansByUserId(@Param("userId") long userId);

long countPlansByUserIdAndStatus(@Param("userId") long userId, @Param("status") String status);

java.time.Instant findLatestPlanCreatedAtByUserId(@Param("userId") long userId);

int clearConfirmedPlanReferences(@Param("userId") long userId, @Param("planId") long planId);

int deletePlanByIdForUser(@Param("id") long id, @Param("userId") long userId);
```

- [ ] **Step 7: Add SQL mappings**

Add to `LearningPlanMapper.xml` after `findPlansByUserId`:

```xml
<select id="findPlansByUserIdPage" resultMap="LearningPlanRowMap">
  SELECT
    id,
    user_id,
    status,
    title,
    plan_json,
    created_at,
    updated_at
  FROM learning_plan
  WHERE user_id = #{userId}
  ORDER BY created_at DESC, id DESC
  LIMIT #{limit}
  OFFSET #{offset}
</select>

<select id="countPlansByUserId" resultType="long">
  SELECT COUNT(*)
  FROM learning_plan
  WHERE user_id = #{userId}
</select>

<select id="countPlansByUserIdAndStatus" resultType="long">
  SELECT COUNT(*)
  FROM learning_plan
  WHERE user_id = #{userId}
    AND status = #{status}
</select>

<select id="findLatestPlanCreatedAtByUserId" resultType="java.time.Instant">
  SELECT MAX(created_at)
  FROM learning_plan
  WHERE user_id = #{userId}
</select>

<update id="clearConfirmedPlanReferences">
  UPDATE learning_plan_draft
  SET confirmed_plan_id = NULL,
      updated_at = NOW()
  WHERE user_id = #{userId}
    AND confirmed_plan_id = #{planId}
</update>

<delete id="deletePlanByIdForUser">
  DELETE FROM learning_plan
  WHERE id = #{id}
    AND user_id = #{userId}
</delete>
```

- [ ] **Step 8: Implement MyBatis repository methods**

Add to `MyBatisLearningPlanRepository.java`:

```java
@Override
public LearningPlanPage findPageByUserId(long userId, int page, int pageSize) {
  int offset = (page - 1) * pageSize;
  List<LearningPlan> items = mapper.findPlansByUserIdPage(userId, pageSize, offset).stream()
      .map(this::toPlan)
      .toList();
  return new LearningPlanPage(
      items,
      mapper.countPlansByUserId(userId),
      page,
      pageSize,
      mapper.countPlansByUserIdAndStatus(userId, LearningPlanStatus.ACTIVE.name()),
      mapper.countPlansByUserIdAndStatus(userId, LearningPlanStatus.ARCHIVED.name()),
      mapper.findLatestPlanCreatedAtByUserId(userId));
}

@Override
public void clearConfirmedPlanReferences(long userId, long planId) {
  mapper.clearConfirmedPlanReferences(userId, planId);
}

@Override
@Transactional
public boolean deletePlanByIdForUser(long planId, long userId) {
  return mapper.deletePlanByIdForUser(planId, userId) > 0;
}
```

Add import:

```java
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanPage;
```

- [ ] **Step 9: Update unavailable repository**

In `UnavailableLearningPlanRepository.java`, implement:

```java
@Override
public LearningPlanPage findPageByUserId(long userId, int page, int pageSize) {
  throw new LearningPlanRepositoryUnavailableException();
}

@Override
public void clearConfirmedPlanReferences(long userId, long planId) {
  throw new LearningPlanRepositoryUnavailableException();
}

@Override
public boolean deletePlanByIdForUser(long planId, long userId) {
  throw new LearningPlanRepositoryUnavailableException();
}
```

- [ ] **Step 10: Run backend tests**

Run:

```bash
mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository -pl mentor-application,mentor-api test
```

Expected: application and API tests pass.

- [ ] **Step 11: Commit**

```bash
git add backend/mentor-application backend/mentor-api
git commit -m "feat: paginate and delete learning plans"
```

---

## Task 3: Frontend API Types, Options, And Request Helpers

**Files:**

- Modify: `frontend/src/types/api.ts`
- Modify: `frontend/src/services/api.ts`
- Modify: `frontend/src/learning-plans/options.ts`
- Create: `frontend/src/learning-plans/options.test.ts`

- [ ] **Step 1: Write option/helper tests**

Create `frontend/src/learning-plans/options.test.ts`:

```ts
import { describe, expect, it } from 'vitest';
import {
  buildLearningPlanGoal,
  difficultyDistributionOptions,
  topicOptions,
} from './options';

describe('learning plan options', () => {
  it('maps difficulty distribution to visible percentages and backend preference', () => {
    expect(difficultyDistributionOptions.find((option) => option.value === 'BALANCED')).toMatchObject({
      preference: 'MIXED',
      easyPercent: 25,
      mediumPercent: 55,
      hardPercent: 20,
    });
  });

  it('maps Chinese topic labels to backend tags', () => {
    expect(topicOptions.find((option) => option.label === '动态规划')).toMatchObject({
      value: 'Dynamic Programming',
    });
  });

  it('builds a goal from questionnaire fields and optional notes', () => {
    expect(buildLearningPlanGoal({
      intentLabel: '面试冲刺',
      durationWeeks: 4,
      weeklyHours: 6,
      levelLabel: '中级',
      programmingLanguage: 'Java',
      difficultyLabel: '均衡',
      easyPercent: 25,
      mediumPercent: 55,
      hardPercent: 20,
      topics: ['Array', 'Hash Table'],
      additionalThoughts: '希望每周留一天复盘。',
    })).toContain('补充想法：希望每周留一天复盘。');
  });
});
```

- [ ] **Step 2: Run frontend tests and verify failure**

Run:

```bash
npm --cache ./.npm --prefix frontend test -- --run frontend/src/learning-plans/options.test.ts
```

Expected: fails because the new exports do not exist.

- [ ] **Step 3: Add API types**

In `frontend/src/types/api.ts`, add:

```ts
export interface LearningPlanListQuery {
  page?: number;
  pageSize?: number;
}

export interface LearningPlanPageResponse {
  items: LearningPlanSummaryResponse[];
  total: number;
  page: number;
  pageSize: number;
  activeCount: number;
  archivedCount: number;
  latestCreatedAt?: string | null;
}

export type DifficultyDistributionLevel = 'INTRODUCTORY' | 'BALANCED' | 'SPRINT';
```

- [ ] **Step 4: Update API helpers**

In `frontend/src/services/api.ts`, import `LearningPlanListQuery` and `LearningPlanPageResponse`.

Replace `getLearningPlans` with:

```ts
export async function getLearningPlans(
  query: LearningPlanListQuery = {},
  signal?: AbortSignal,
): Promise<ApiResponse<LearningPlanPageResponse>> {
  const response = await fetch(`/api/learning-plans${toQueryString(query)}`, {
    headers: jsonHeaders,
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Learning plans request failed');
  }

  return response.json();
}
```

Add:

```ts
export async function deleteLearningPlan(planId: number): Promise<ApiResponse<void>> {
  const response = await apiFetch(`/api/learning-plans/${planId}`, {
    method: 'DELETE',
    headers: jsonHeaders,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Learning plan delete request failed');
  }

  return response.json();
}
```

- [ ] **Step 5: Replace learning plan options**

In `frontend/src/learning-plans/options.ts`, add:

```ts
export const planScenarioOptions = [
  { label: '面试冲刺', value: 'INTERVIEW_SPRINT', interviewOriented: true },
  { label: '专项突破', value: 'TOPIC_BREAKTHROUGH', interviewOriented: false },
  { label: '基础巩固', value: 'PRACTICE_GOAL', interviewOriented: false },
  { label: '错题复盘', value: 'MISTAKE_REVIEW', interviewOriented: false },
  { label: '长期学习', value: 'LONG_TERM_LEARNING', interviewOriented: false },
] as const;

export const programmingLanguageOptions = [
  'Java',
  'Python3',
  'C++',
  'JavaScript',
  'TypeScript',
  'Go',
  'C#',
  'C',
  'Kotlin',
  'Swift',
  'Rust',
] as const;

export const difficultyDistributionOptions = [
  { label: '入门', value: 'INTRODUCTORY', preference: 'EASY', easyPercent: 60, mediumPercent: 35, hardPercent: 5 },
  { label: '均衡', value: 'BALANCED', preference: 'MIXED', easyPercent: 25, mediumPercent: 55, hardPercent: 20 },
  { label: '冲刺', value: 'SPRINT', preference: 'HARD', easyPercent: 10, mediumPercent: 55, hardPercent: 35 },
] as const;

export const topicOptions = [
  { label: '数组', value: 'Array' },
  { label: '哈希表', value: 'Hash Table' },
  { label: '字符串', value: 'String' },
  { label: '双指针', value: 'Two Pointers' },
  { label: '滑动窗口', value: 'Sliding Window' },
  { label: '栈', value: 'Stack' },
  { label: '队列', value: 'Queue' },
  { label: '链表', value: 'Linked List' },
  { label: '二叉树', value: 'Binary Tree' },
  { label: '图', value: 'Graph' },
  { label: 'DFS/BFS', value: 'Depth-First Search' },
  { label: '二分查找', value: 'Binary Search' },
  { label: '动态规划', value: 'Dynamic Programming' },
  { label: '贪心', value: 'Greedy' },
  { label: '堆', value: 'Heap' },
  { label: '回溯', value: 'Backtracking' },
  { label: '位运算', value: 'Bit Manipulation' },
] as const;

export interface BuildGoalInput {
  intentLabel: string;
  durationWeeks: number;
  weeklyHours: number;
  levelLabel: string;
  programmingLanguage: string;
  difficultyLabel: string;
  easyPercent: number;
  mediumPercent: number;
  hardPercent: number;
  topics: string[];
  additionalThoughts: string;
}

export function buildLearningPlanGoal(input: BuildGoalInput): string {
  return [
    `计划场景：${input.intentLabel}`,
    `周期：${input.durationWeeks} 周`,
    `每周投入：${input.weeklyHours} 小时`,
    `当前水平：${input.levelLabel}`,
    `编程语言：${input.programmingLanguage}`,
    `难度分布：简单 ${input.easyPercent}%，中等 ${input.mediumPercent}%，困难 ${input.hardPercent}%`,
    input.topics.length > 0 ? `主题偏好：${input.topics.join(', ')}` : '主题偏好：由系统根据计划场景安排',
    input.additionalThoughts.trim() ? `补充想法：${input.additionalThoughts.trim()}` : undefined,
  ].filter(Boolean).join('\n');
}
```

- [ ] **Step 6: Run frontend option tests**

Run:

```bash
npm --cache ./.npm --prefix frontend test -- --run frontend/src/learning-plans/options.test.ts
```

Expected: tests pass.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/types/api.ts frontend/src/services/api.ts frontend/src/learning-plans/options.ts frontend/src/learning-plans/options.test.ts
git commit -m "feat: add learning plan frontend contracts"
```

---

## Task 4: Create Modal And Difficulty Distribution Control

**Files:**

- Create: `frontend/src/learning-plans/DifficultyDistributionControl.tsx`
- Create: `frontend/src/learning-plans/DifficultyDistributionControl.test.tsx`
- Create: `frontend/src/learning-plans/LearningPlanCreateModal.tsx`
- Create: `frontend/src/learning-plans/LearningPlanCreateModal.test.tsx`
- Remove after replacement: `frontend/src/learning-plans/LearningPlanWizard.tsx`
- Remove after replacement: `frontend/src/learning-plans/LearningPlanWizard.test.tsx`

- [ ] **Step 1: Write modal/control tests**

Create `DifficultyDistributionControl.test.tsx`:

```tsx
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import DifficultyDistributionControl from './DifficultyDistributionControl';

afterEach(cleanup);

describe('DifficultyDistributionControl', () => {
  it('shows the selected distribution percentages and emits changes', () => {
    const onChange = vi.fn();

    render(<DifficultyDistributionControl disabled={false} onChange={onChange} value="BALANCED" />);

    expect(screen.getByText('简单 25%')).toBeInTheDocument();
    expect(screen.getByText('中等 55%')).toBeInTheDocument();
    expect(screen.getByText('困难 20%')).toBeInTheDocument();

    fireEvent.change(screen.getByRole('slider', { name: '难度分布' }), { target: { value: '2' } });

    expect(onChange).toHaveBeenCalledWith('SPRINT');
  });
});
```

Create `LearningPlanCreateModal.test.tsx`:

```tsx
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import LearningPlanCreateModal from './LearningPlanCreateModal';

afterEach(cleanup);

describe('LearningPlanCreateModal', () => {
  it('submits a generated goal without a separate learning goal field', () => {
    const onSubmit = vi.fn();

    render(<LearningPlanCreateModal loading={false} open onClose={vi.fn()} onSubmit={onSubmit} />);

    expect(screen.queryByRole('textbox', { name: '学习目标' })).not.toBeInTheDocument();
    fireEvent.change(screen.getByRole('spinbutton', { name: '计划周期' }), { target: { value: '6' } });
    fireEvent.change(screen.getByRole('spinbutton', { name: '每周投入' }), { target: { value: '8' } });
    fireEvent.change(screen.getByRole('combobox', { name: '编程语言' }), { target: { value: 'Python3' } });
    fireEvent.click(screen.getByRole('button', { name: '动态规划' }));
    fireEvent.change(screen.getByRole('textbox', { name: '补充想法' }), {
      target: { value: '希望每周留一天复盘。' },
    });
    fireEvent.click(screen.getByRole('button', { name: '生成计划草案' }));

    expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({
      intent: 'INTERVIEW_SPRINT',
      durationWeeks: 6,
      weeklyHours: 8,
      programmingLanguage: 'Python3',
      difficultyPreference: 'MIXED',
      interviewOriented: true,
      topicPreferences: ['Dynamic Programming'],
    }));
    expect(onSubmit.mock.calls[0][0].goal).toContain('补充想法：希望每周留一天复盘。');
  });

  it('requires a topic for topic breakthrough scenario', () => {
    const onSubmit = vi.fn();

    render(<LearningPlanCreateModal loading={false} open onClose={vi.fn()} onSubmit={onSubmit} />);

    fireEvent.click(screen.getByRole('button', { name: '专项突破' }));
    fireEvent.click(screen.getByRole('button', { name: '生成计划草案' }));

    expect(screen.getByText('专项突破需要至少选择一个主题。')).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
  });
});
```

- [ ] **Step 2: Run failing modal tests**

Run:

```bash
npm --cache ./.npm --prefix frontend test -- --run frontend/src/learning-plans/DifficultyDistributionControl.test.tsx frontend/src/learning-plans/LearningPlanCreateModal.test.tsx
```

Expected: fails because components do not exist.

- [ ] **Step 3: Implement difficulty control**

Create `DifficultyDistributionControl.tsx`:

```tsx
import type { DifficultyDistributionLevel } from '../types/api';
import { difficultyDistributionOptions } from './options';

interface DifficultyDistributionControlProps {
  disabled: boolean;
  value: DifficultyDistributionLevel;
  onChange: (value: DifficultyDistributionLevel) => void;
}

export default function DifficultyDistributionControl({
  disabled,
  value,
  onChange,
}: DifficultyDistributionControlProps) {
  const selectedIndex = Math.max(0, difficultyDistributionOptions.findIndex((option) => option.value === value));
  const selected = difficultyDistributionOptions[selectedIndex] ?? difficultyDistributionOptions[0];

  return (
    <div className="difficulty-control">
      <label className="topic-field">
        <span>难度分布</span>
        <input
          aria-label="难度分布"
          disabled={disabled}
          max={difficultyDistributionOptions.length - 1}
          min={0}
          onChange={(event) => {
            const next = difficultyDistributionOptions[Number(event.target.value)];
            onChange(next.value);
          }}
          type="range"
          value={selectedIndex}
        />
      </label>
      <div className="difficulty-ratio-row">
        <span>简单 {selected.easyPercent}%</span>
        <span>中等 {selected.mediumPercent}%</span>
        <span>困难 {selected.hardPercent}%</span>
      </div>
    </div>
  );
}
```

- [ ] **Step 4: Implement create modal**

Create `LearningPlanCreateModal.tsx` with local state and `onSubmit` mapping:

```tsx
import { X } from 'lucide-react';
import { useMemo, useState } from 'react';
import type {
  DifficultyDistributionLevel,
  LearningPlanCreateDraftRequest,
  LearningPlanDifficultyPreference,
  LearningPlanIntent,
  LearningPlanLevel,
} from '../types/api';
import DifficultyDistributionControl from './DifficultyDistributionControl';
import {
  buildLearningPlanGoal,
  difficultyDistributionOptions,
  levelOptions,
  planScenarioOptions,
  programmingLanguageOptions,
  topicOptions,
} from './options';

interface LearningPlanCreateModalProps {
  open: boolean;
  loading: boolean;
  error?: string;
  onClose: () => void;
  onSubmit: (request: LearningPlanCreateDraftRequest) => void;
}

export default function LearningPlanCreateModal({
  open,
  loading,
  error,
  onClose,
  onSubmit,
}: LearningPlanCreateModalProps) {
  const [intent, setIntent] = useState<LearningPlanIntent>('INTERVIEW_SPRINT');
  const [durationWeeks, setDurationWeeks] = useState(4);
  const [weeklyHours, setWeeklyHours] = useState(6);
  const [level, setLevel] = useState<LearningPlanLevel>('INTERMEDIATE');
  const [programmingLanguage, setProgrammingLanguage] = useState('Java');
  const [difficultyLevel, setDifficultyLevel] = useState<DifficultyDistributionLevel>('BALANCED');
  const [topicPreferences, setTopicPreferences] = useState<string[]>([]);
  const [additionalThoughts, setAdditionalThoughts] = useState('');
  const [validationError, setValidationError] = useState('');

  const numericValid = Number.isInteger(durationWeeks) && durationWeeks > 0
    && Number.isInteger(weeklyHours) && weeklyHours > 0;
  const selectedScenario = planScenarioOptions.find((option) => option.value === intent) ?? planScenarioOptions[0];
  const selectedLevel = levelOptions.find((option) => option.value === level) ?? levelOptions[1];
const selectedDifficulty = difficultyDistributionOptions.find((option) => option.value === difficultyLevel)
    ?? difficultyDistributionOptions[1];
  const difficultyPreference = selectedDifficulty.preference as LearningPlanDifficultyPreference;

  const hasUnsavedInput = useMemo(
    () => additionalThoughts.trim().length > 0 || topicPreferences.length > 0 || durationWeeks !== 4 || weeklyHours !== 6,
    [additionalThoughts, durationWeeks, topicPreferences.length, weeklyHours],
  );

  if (!open) {
    return null;
  }

  function close() {
    if (loading) {
      return;
    }
    if (hasUnsavedInput && !window.confirm('放弃当前填写的计划问卷？')) {
      return;
    }
    onClose();
  }

  function toggleTopic(value: string) {
    setTopicPreferences((current) => (
      current.includes(value) ? current.filter((topic) => topic !== value) : [...current, value]
    ));
  }

  function submit() {
    if (!numericValid) {
      setValidationError('周期和每周投入必须是正整数。');
      return;
    }
    if (intent === 'TOPIC_BREAKTHROUGH' && topicPreferences.length === 0) {
      setValidationError('专项突破需要至少选择一个主题。');
      return;
    }
    setValidationError('');
    onSubmit({
      intent,
      goal: buildLearningPlanGoal({
        intentLabel: selectedScenario.label,
        durationWeeks,
        weeklyHours,
        levelLabel: selectedLevel.label,
        programmingLanguage,
        difficultyLabel: selectedDifficulty.label,
        easyPercent: selectedDifficulty.easyPercent,
        mediumPercent: selectedDifficulty.mediumPercent,
        hardPercent: selectedDifficulty.hardPercent,
        topics: topicPreferences,
        additionalThoughts,
      }),
      durationWeeks,
      level,
      weeklyHours,
      programmingLanguage,
      difficultyPreference,
      interviewOriented: selectedScenario.interviewOriented,
      topicPreferences,
    });
  }

  return (
    <div aria-modal="true" className="modal-backdrop" role="dialog">
      <section aria-labelledby="create-plan-title" className="create-plan-modal">
        <div className="modal-heading">
          <div>
            <p className="eyebrow">新建计划</p>
            <h2 id="create-plan-title">新建学习计划</h2>
          </div>
          <button aria-label="关闭" className="icon-button" disabled={loading} onClick={close} type="button">
            <X aria-hidden="true" />
          </button>
        </div>
        {(error || validationError) && <p className="error-text">{validationError || error}</p>}
        <div className="modal-form">
          <section className="question-block">
            <strong>计划场景</strong>
            <div className="segmented-grid">
              {planScenarioOptions.map((option) => (
                <button className={intent === option.value ? 'selected' : ''} disabled={loading} key={option.value} onClick={() => setIntent(option.value)} type="button">
                  {option.label}
                </button>
              ))}
            </div>
          </section>
          <div className="mini-grid">
            <label className="topic-field">
              <span>周期</span>
              <input aria-label="计划周期" disabled={loading} min={1} onChange={(event) => setDurationWeeks(Number(event.target.value))} type="number" value={durationWeeks} />
            </label>
            <label className="topic-field">
              <span>每周投入</span>
              <input aria-label="每周投入" disabled={loading} min={1} onChange={(event) => setWeeklyHours(Number(event.target.value))} type="number" value={weeklyHours} />
            </label>
          </div>
          <div className="mini-grid">
            <label className="topic-field">
              <span>当前水平</span>
              <select aria-label="当前水平" disabled={loading} onChange={(event) => setLevel(event.target.value as LearningPlanLevel)} value={level}>
                {levelOptions.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
              </select>
            </label>
            <label className="topic-field">
              <span>编程语言</span>
              <select aria-label="编程语言" disabled={loading} onChange={(event) => setProgrammingLanguage(event.target.value)} value={programmingLanguage}>
                {programmingLanguageOptions.map((option) => <option key={option} value={option}>{option}</option>)}
              </select>
            </label>
          </div>
          <DifficultyDistributionControl disabled={loading} onChange={setDifficultyLevel} value={difficultyLevel} />
          <section className="question-block">
            <strong>主题偏好</strong>
            <div className="topic-option-grid">
              {topicOptions.map((option) => (
                <button className={topicPreferences.includes(option.value) ? 'selected' : ''} disabled={loading} key={option.value} onClick={() => toggleTopic(option.value)} type="button">
                  {option.label}
                </button>
              ))}
            </div>
          </section>
          <label className="topic-field">
            <span>补充想法</span>
            <textarea aria-label="补充想法" disabled={loading} onChange={(event) => setAdditionalThoughts(event.target.value)} rows={4} value={additionalThoughts} />
          </label>
        </div>
        <div className="modal-actions">
          <button className="secondary-button" disabled={loading} onClick={close} type="button">取消</button>
          <button className="primary-button" disabled={loading} onClick={submit} type="button">{loading ? '生成中' : '生成计划草案'}</button>
        </div>
      </section>
    </div>
  );
}
```

- [ ] **Step 5: Run modal/control tests**

Run:

```bash
npm --cache ./.npm --prefix frontend test -- --run frontend/src/learning-plans/DifficultyDistributionControl.test.tsx frontend/src/learning-plans/LearningPlanCreateModal.test.tsx
```

Expected: tests pass.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/learning-plans/DifficultyDistributionControl.tsx \
  frontend/src/learning-plans/DifficultyDistributionControl.test.tsx \
  frontend/src/learning-plans/LearningPlanCreateModal.tsx \
  frontend/src/learning-plans/LearningPlanCreateModal.test.tsx
git commit -m "feat: add learning plan create modal"
```

---

## Task 5: Summary Card And Paginated List Card

**Files:**

- Create: `frontend/src/learning-plans/LearningPlanSummaryCard.tsx`
- Create: `frontend/src/learning-plans/LearningPlanSummaryCard.test.tsx`
- Create: `frontend/src/learning-plans/LearningPlanListCard.tsx`
- Create: `frontend/src/learning-plans/LearningPlanListCard.test.tsx`

- [ ] **Step 1: Write component tests**

Create `LearningPlanSummaryCard.test.tsx`:

```tsx
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import LearningPlanSummaryCard from './LearningPlanSummaryCard';

afterEach(cleanup);

describe('LearningPlanSummaryCard', () => {
  it('shows aggregate counts and the create action', () => {
    const onCreate = vi.fn();

    render(<LearningPlanSummaryCard activeCount={8} archivedCount={4} latestCreatedAt="2026-06-22T00:00:00Z" onCreate={onCreate} total={12} />);

    expect(screen.getByText('当前共有 12 个计划')).toBeInTheDocument();
    expect(screen.getByText('8')).toBeInTheDocument();
    expect(screen.getByText('进行中')).toBeInTheDocument();
    expect(screen.getByText('4')).toBeInTheDocument();
    expect(screen.getByText('已归档')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '新建计划' }));
    expect(onCreate).toHaveBeenCalledTimes(1);
  });
});
```

Create `LearningPlanListCard.test.tsx`:

```tsx
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { LearningPlanPageResponse } from '../types/api';
import LearningPlanListCard from './LearningPlanListCard';

afterEach(cleanup);

describe('LearningPlanListCard', () => {
  const page: LearningPlanPageResponse = {
    items: [{
      id: 900,
      title: '四周 Java 算法面试冲刺计划',
      intent: 'INTERVIEW_SPRINT',
      goal: '准备 Java 后端算法面试',
      durationWeeks: 4,
      level: 'INTERMEDIATE',
      weeklyHours: 6,
      status: 'ACTIVE',
      createdAt: '2026-06-22T00:00:00Z',
    }],
    total: 12,
    page: 1,
    pageSize: 10,
    activeCount: 8,
    archivedCount: 4,
    latestCreatedAt: '2026-06-22T00:00:00Z',
  };

  it('renders plan content on the left and actions on the right', () => {
    const onSelect = vi.fn();
    const onDelete = vi.fn();

    render(<LearningPlanListCard deletingPlanId={undefined} onDelete={onDelete} onPageChange={vi.fn()} onSelect={onSelect} page={page} selectedPlanId={900} />);

    expect(screen.getByText('四周 Java 算法面试冲刺计划')).toBeInTheDocument();
    expect(screen.getByText('准备 Java 后端算法面试')).toBeInTheDocument();
    expect(screen.getByText('ACTIVE')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '查看 四周 Java 算法面试冲刺计划' }));
    expect(onSelect).toHaveBeenCalledWith(900);

    fireEvent.click(screen.getByRole('button', { name: '删除 四周 Java 算法面试冲刺计划' }));
    expect(onDelete).toHaveBeenCalledWith(900);
  });

  it('moves to the next page', () => {
    const onPageChange = vi.fn();
    render(<LearningPlanListCard deletingPlanId={undefined} onDelete={vi.fn()} onPageChange={onPageChange} onSelect={vi.fn()} page={page} selectedPlanId={undefined} />);

    fireEvent.click(screen.getByRole('button', { name: '下一页' }));

    expect(onPageChange).toHaveBeenCalledWith(2);
  });
});
```

- [ ] **Step 2: Run failing component tests**

Run:

```bash
npm --cache ./.npm --prefix frontend test -- --run frontend/src/learning-plans/LearningPlanSummaryCard.test.tsx frontend/src/learning-plans/LearningPlanListCard.test.tsx
```

Expected: fails because components do not exist.

- [ ] **Step 3: Implement summary card**

Create `LearningPlanSummaryCard.tsx`:

```tsx
import { Plus } from 'lucide-react';

interface LearningPlanSummaryCardProps {
  total: number;
  activeCount: number;
  archivedCount: number;
  latestCreatedAt?: string | null;
  onCreate: () => void;
}

function formatDate(value?: string | null): string {
  if (!value) {
    return '暂无';
  }
  return new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit' }).format(new Date(value));
}

export default function LearningPlanSummaryCard({
  total,
  activeCount,
  archivedCount,
  latestCreatedAt,
  onCreate,
}: LearningPlanSummaryCardProps) {
  return (
    <article className="learning-panel plan-summary-card">
      <div className="plan-summary-content">
        <div>
          <p className="eyebrow">学习计划</p>
          <h2>当前共有 {total} 个计划</h2>
        </div>
        <div className="summary-metric-row">
          <span><strong>{activeCount}</strong><small>进行中</small></span>
          <span><strong>{archivedCount}</strong><small>已归档</small></span>
          <span><strong>{formatDate(latestCreatedAt)}</strong><small>最近创建</small></span>
        </div>
      </div>
      <button className="primary-button" onClick={onCreate} type="button">
        <Plus aria-hidden="true" />
        <span>新建计划</span>
      </button>
    </article>
  );
}
```

- [ ] **Step 4: Implement list card**

Create `LearningPlanListCard.tsx`:

```tsx
import { Eye, Trash2 } from 'lucide-react';
import type { LearningPlanPageResponse } from '../types/api';

interface LearningPlanListCardProps {
  page: LearningPlanPageResponse;
  selectedPlanId?: number;
  deletingPlanId?: number;
  onSelect: (planId: number) => void;
  onDelete: (planId: number) => void;
  onPageChange: (page: number) => void;
}

export default function LearningPlanListCard({
  page,
  selectedPlanId,
  deletingPlanId,
  onSelect,
  onDelete,
  onPageChange,
}: LearningPlanListCardProps) {
  const totalPages = Math.max(1, Math.ceil(page.total / page.pageSize));

  return (
    <article className="learning-panel plan-list-card">
      <div className="panel-title compact-title">
        <h2>计划列表</h2>
        <span>第 {page.page} / {totalPages} 页</span>
      </div>
      <div className="plan-list">
        {page.items.length === 0 ? (
          <p className="empty-log">暂无正式计划，先新建一个学习计划。</p>
        ) : page.items.map((plan) => (
          <div className={`plan-list-row ${selectedPlanId === plan.id ? 'selected' : ''}`} key={plan.id}>
            <button className="plan-row-main" onClick={() => onSelect(plan.id)} type="button">
              <strong>{plan.title}</strong>
              <span>{plan.goal}</span>
              <small>{plan.durationWeeks} 周 · {plan.weeklyHours} 小时/周</small>
            </button>
            <div className="plan-row-actions">
              <span className="status-badge">{plan.status}</span>
              <button aria-label={`查看 ${plan.title}`} className="secondary-button compact" onClick={() => onSelect(plan.id)} type="button">
                <Eye aria-hidden="true" />
                <span>查看</span>
              </button>
              <button aria-label={`删除 ${plan.title}`} className="danger-button compact" disabled={deletingPlanId === plan.id} onClick={() => onDelete(plan.id)} type="button">
                <Trash2 aria-hidden="true" />
                <span>{deletingPlanId === plan.id ? '删除中' : '删除'}</span>
              </button>
            </div>
          </div>
        ))}
      </div>
      <div className="pagination-row">
        <button className="secondary-button compact" disabled={page.page <= 1} onClick={() => onPageChange(page.page - 1)} type="button">上一页</button>
        <button className="secondary-button compact" disabled={page.page >= totalPages} onClick={() => onPageChange(page.page + 1)} type="button">下一页</button>
      </div>
    </article>
  );
}
```

- [ ] **Step 5: Run card tests**

Run:

```bash
npm --cache ./.npm --prefix frontend test -- --run frontend/src/learning-plans/LearningPlanSummaryCard.test.tsx frontend/src/learning-plans/LearningPlanListCard.test.tsx
```

Expected: tests pass.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/learning-plans/LearningPlanSummaryCard.tsx \
  frontend/src/learning-plans/LearningPlanSummaryCard.test.tsx \
  frontend/src/learning-plans/LearningPlanListCard.tsx \
  frontend/src/learning-plans/LearningPlanListCard.test.tsx
git commit -m "feat: add learning plan management cards"
```

---

## Task 6: Wire The Redesigned LearningPlans Page

**Files:**

- Modify: `frontend/src/LearningPlans.tsx`
- Modify: `frontend/src/learning-plans/LearningPlanDraftPanel.tsx`
- Modify: `frontend/src/learning-plans/LearningPlanDraftPanel.test.tsx`
- Modify: `frontend/src/App.test.tsx`

- [ ] **Step 1: Update draft panel tests for editable goal regeneration**

Add to `LearningPlanDraftPanel.test.tsx`:

```tsx
it('allows editing the goal summary and asks for regeneration', () => {
  const onRegenerate = vi.fn();
  const draft: LearningPlanDraftResponse = {
    draftId: 100,
    status: 'GENERATED',
    assistantMessage: '已生成学习计划草案。',
    missingFields: [],
    draftPlan,
  };

  render(
    <LearningPlanDraftPanel
      draft={draft}
      loading={false}
      onConfirm={vi.fn()}
      onRegenerateGoal={onRegenerate}
      onReturnToWizard={vi.fn()}
      onSendFollowUp={vi.fn()}
    />,
  );

  fireEvent.click(screen.getByRole('button', { name: '编辑目标摘要' }));
  fireEvent.change(screen.getByRole('textbox', { name: '目标摘要' }), {
    target: { value: '改成动态规划冲刺目标。' },
  });
  fireEvent.click(screen.getByRole('button', { name: '按新目标重新生成' }));

  expect(onRegenerate).toHaveBeenCalledWith('改成动态规划冲刺目标。');
});
```

- [ ] **Step 2: Update App integration tests**

Update learning plan mocks to return page objects:

```ts
function learningPlanPage(items = [learningPlanSummary()], overrides = {}) {
  return {
    items,
    total: items.length,
    page: 1,
    pageSize: 10,
    activeCount: items.filter((item) => item.status === 'ACTIVE').length,
    archivedCount: items.filter((item) => item.status === 'ARCHIVED').length,
    latestCreatedAt: items[0]?.createdAt ?? null,
    ...overrides,
  };
}
```

Replace mock list responses:

```ts
data: learningPlanPage([learningPlanSummary()])
```

Update the main create test to:

```ts
expect(await screen.findByText('当前共有 1 个计划')).toBeInTheDocument();
fireEvent.click(screen.getByRole('button', { name: '新建计划' }));
expect(screen.queryByRole('textbox', { name: '学习目标' })).not.toBeInTheDocument();
fireEvent.click(screen.getByRole('button', { name: '动态规划' }));
fireEvent.click(screen.getByRole('button', { name: '生成计划草案' }));
expectCsrfHeader(fetchMock, '/api/learning-plans/drafts');
```

Add a delete integration test:

```ts
it('deletes a learning plan and refreshes the current page', async () => {
  const fetchMock = mockLearningPlanDeleteFetch();
  vi.stubGlobal('fetch', fetchMock);
  window.history.replaceState({}, '', '/learning-plans');

  render(<App />);

  expect(await screen.findByText('四周 Java 算法面试冲刺计划')).toBeInTheDocument();
  vi.spyOn(window, 'confirm').mockReturnValue(true);
  fireEvent.click(screen.getByRole('button', { name: '删除 四周 Java 算法面试冲刺计划' }));

  await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(
    '/api/learning-plans/900',
    expect.objectContaining({ method: 'DELETE' }),
  ));
  expectCsrfHeader(fetchMock, '/api/learning-plans/900');
});
```

- [ ] **Step 3: Run failing integration tests**

Run:

```bash
npm --cache ./.npm --prefix frontend test -- --run frontend/src/learning-plans/LearningPlanDraftPanel.test.tsx frontend/src/App.test.tsx
```

Expected: fails because page orchestration still uses old wizard/list shape.

- [ ] **Step 4: Update draft panel props and behavior**

Modify `LearningPlanDraftPanel.tsx` props:

```ts
onRegenerateGoal?: (goal: string) => void;
```

In generated draft rendering, add a goal editor above `PlanPreview`:

```tsx
const [editingGoal, setEditingGoal] = useState(false);
const [goalDraft, setGoalDraft] = useState(draft.draftPlan?.goal ?? '');
```

Render controls:

```tsx
{editingGoal ? (
  <div className="goal-editor">
    <label className="topic-field">
      <span>目标摘要</span>
      <textarea aria-label="目标摘要" disabled={loading} onChange={(event) => setGoalDraft(event.target.value)} rows={3} value={goalDraft} />
    </label>
    <button className="secondary-button" disabled={loading || !goalDraft.trim()} onClick={() => onRegenerateGoal?.(goalDraft.trim())} type="button">
      按新目标重新生成
    </button>
  </div>
) : (
  <div className="goal-summary">
    <p>{draft.draftPlan.goal}</p>
    {onRegenerateGoal && (
      <button className="secondary-button compact" disabled={loading} onClick={() => setEditingGoal(true)} type="button">
        编辑目标摘要
      </button>
    )}
  </div>
)}
```

- [ ] **Step 5: Replace `LearningPlans.tsx` orchestration**

Update imports:

```ts
import LearningPlanCreateModal from './learning-plans/LearningPlanCreateModal';
import LearningPlanListCard from './learning-plans/LearningPlanListCard';
import LearningPlanSummaryCard from './learning-plans/LearningPlanSummaryCard';
import {
  confirmLearningPlanDraft,
  createLearningPlanDraft,
  deleteLearningPlan,
  getLearningPlanDetail,
  getLearningPlans,
  sendLearningPlanDraftMessage,
} from './services/api';
```

Replace list state:

```ts
const [plansPage, setPlansPage] = useState<LearningPlanPageResponse>({
  items: [],
  total: 0,
  page: 1,
  pageSize: 10,
  activeCount: 0,
  archivedCount: 0,
  latestCreatedAt: null,
});
const [page, setPage] = useState(1);
const [isCreateModalOpen, setCreateModalOpen] = useState(false);
const [createModalKey, setCreateModalKey] = useState(0);
const [modalError, setModalError] = useState('');
const [deletingPlanId, setDeletingPlanId] = useState<number>();
```

Update loading:

```ts
async function refreshPlans(nextPage = page, selectedId?: number) {
  const nextPlans = apiData(await getLearningPlans({ page: nextPage, pageSize: plansPage.pageSize }), '学习计划列表加载失败');
  setPlansPage(nextPlans);
  setPage(nextPlans.page);
  if (selectedId) {
    await loadPlan(selectedId);
  } else if (!selectedPlan && nextPlans.items[0]) {
    await loadPlan(nextPlans.items[0].id);
  }
}
```

Update submit:

```ts
async function submitDraft(request: LearningPlanCreateDraftRequest) {
  setFlowState('generating');
  setModalError('');
  try {
    const nextDraft = apiData(await createLearningPlanDraft(request), '学习计划草案创建失败');
    setDraft(nextDraft);
    setSelectedPlan(undefined);
    setCreateModalOpen(false);
    setFlowState(nextDraft.status === 'COLLECTING' ? 'collecting' : 'previewing');
  } catch (nextError) {
    setModalError(nextError instanceof Error ? nextError.message : '学习计划草案创建失败');
    setFlowState('creating');
  }
}
```

Add delete:

```ts
async function removePlan(planId: number) {
  if (!window.confirm('确认删除这个学习计划？')) {
    return;
  }
  setDeletingPlanId(planId);
  setError('');
  try {
    await deleteLearningPlan(planId);
    const shouldStepBack = plansPage.items.length === 1 && page > 1;
    const nextPage = shouldStepBack ? page - 1 : page;
    setSelectedPlan((current) => current?.id === planId ? undefined : current);
    await refreshPlans(nextPage);
  } catch (nextError) {
    setError(nextError instanceof Error ? nextError.message : '学习计划删除失败');
  } finally {
    setDeletingPlanId(undefined);
  }
}
```

Add regenerate:

```ts
async function regenerateFromGoal(goal: string) {
  return sendFollowUp(`请按新的目标摘要重新生成学习计划：${goal}`);
}
```

Render:

```tsx
<section className="learning-shell" aria-label="学习计划">
  <LearningPlanSummaryCard
    activeCount={plansPage.activeCount}
    archivedCount={plansPage.archivedCount}
    latestCreatedAt={plansPage.latestCreatedAt}
    onCreate={() => {
      setModalError('');
      setCreateModalKey((current) => current + 1);
      setCreateModalOpen(true);
      setFlowState('creating');
    }}
    total={plansPage.total}
  />

  {error && <p className="error-text">{error}</p>}

  <LearningPlanListCard
    deletingPlanId={deletingPlanId}
    onDelete={removePlan}
    onPageChange={(nextPage) => {
      setPage(nextPage);
      void refreshPlans(nextPage);
    }}
    onSelect={(planId) => {
      setDraft(undefined);
      setFlowState('idle');
      void loadPlan(planId);
    }}
    page={plansPage}
    selectedPlanId={selectedPlan?.id}
  />

  <div className="learning-detail-area">
    {draft ? (
      <LearningPlanDraftPanel
        draft={draft}
        loading={flowState === 'generating' || flowState === 'confirming'}
        onConfirm={confirmDraft}
        onRegenerateGoal={regenerateFromGoal}
        onReturnToWizard={() => setCreateModalOpen(true)}
        onSendFollowUp={sendFollowUp}
      />
    ) : selectedPlan ? (
      <LearningPlanDetail plan={selectedPlan} />
    ) : (
      <article className="learning-panel empty-plan-panel">
        <h2>还没有学习计划</h2>
        <p>创建一个计划后，系统会在这里展示阶段、推荐题目和复盘建议。</p>
      </article>
    )}
  </div>

  <LearningPlanCreateModal
    error={modalError}
    key={createModalKey}
    loading={flowState === 'generating'}
    onClose={() => {
      setCreateModalOpen(false);
      setFlowState('idle');
    }}
    onSubmit={submitDraft}
    open={isCreateModalOpen}
  />
</section>
```

- [ ] **Step 6: Remove old wizard import and files**

Remove any `LearningPlanWizard` import from `LearningPlans.tsx`, then:

```bash
git rm frontend/src/learning-plans/LearningPlanWizard.tsx frontend/src/learning-plans/LearningPlanWizard.test.tsx
```

- [ ] **Step 7: Run integration tests**

Run:

```bash
npm --cache ./.npm --prefix frontend test -- --run frontend/src/learning-plans/LearningPlanDraftPanel.test.tsx frontend/src/App.test.tsx
```

Expected: tests pass.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/LearningPlans.tsx frontend/src/learning-plans/LearningPlanDraftPanel.tsx frontend/src/learning-plans/LearningPlanDraftPanel.test.tsx frontend/src/App.test.tsx
git add -u frontend/src/learning-plans/LearningPlanWizard.tsx frontend/src/learning-plans/LearningPlanWizard.test.tsx
git commit -m "feat: redesign learning plan page flow"
```

---

## Task 7: Responsive Styling And Visual Polish

**Files:**

- Modify: `frontend/src/styles.css`
- Modify: `frontend/src/styles.test.tsx` if existing structural CSS tests fail or need assertions.

- [ ] **Step 1: Add layout CSS**

Append or update styles in `frontend/src/styles.css`:

```css
.plan-summary-card {
  align-items: center;
  display: flex;
  gap: 20px;
  justify-content: space-between;
}

.plan-summary-content {
  display: grid;
  gap: 12px;
  min-width: 0;
}

.summary-metric-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.summary-metric-row span {
  background: #f8fafc;
  border: 1px solid #d7dde8;
  border-radius: 8px;
  display: inline-flex;
  gap: 6px;
  padding: 8px 10px;
}

.summary-metric-row small {
  color: #64748b;
}

.plan-list-card {
  display: grid;
  gap: 14px;
}

.plan-list {
  display: grid;
  gap: 10px;
}

.plan-list-row {
  align-items: center;
  background: #fff;
  border: 1px solid #d7dde8;
  border-radius: 8px;
  display: flex;
  gap: 14px;
  justify-content: space-between;
  padding: 12px;
}

.plan-list-row.selected {
  border-color: #2563eb;
  box-shadow: 0 0 0 1px rgba(37, 99, 235, 0.12);
}

.plan-row-main {
  background: transparent;
  border: 0;
  color: inherit;
  cursor: pointer;
  display: grid;
  gap: 4px;
  min-width: 0;
  padding: 0;
  text-align: left;
}

.plan-row-main span,
.plan-row-main small {
  color: #64748b;
}

.plan-row-actions {
  align-items: center;
  display: flex;
  flex-shrink: 0;
  gap: 8px;
}

.danger-button {
  align-items: center;
  background: #fff;
  border: 1px solid #fecaca;
  border-radius: 8px;
  color: #b91c1c;
  display: inline-flex;
  gap: 6px;
  justify-content: center;
  min-height: 36px;
  padding: 0 10px;
}

.modal-backdrop {
  align-items: center;
  background: rgba(15, 23, 42, 0.42);
  display: flex;
  inset: 0;
  justify-content: center;
  padding: 20px;
  position: fixed;
  z-index: 30;
}

.create-plan-modal {
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 24px 80px rgba(15, 23, 42, 0.26);
  display: grid;
  gap: 16px;
  max-height: min(760px, calc(100vh - 40px));
  max-width: 760px;
  overflow: auto;
  padding: 20px;
  width: min(760px, 100%);
}

.modal-heading,
.modal-actions {
  align-items: center;
  display: flex;
  gap: 12px;
  justify-content: space-between;
}

.modal-form {
  display: grid;
  gap: 16px;
}

.question-block {
  display: grid;
  gap: 10px;
}

.segmented-grid,
.topic-option-grid,
.difficulty-ratio-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.segmented-grid button,
.topic-option-grid button {
  background: #fff;
  border: 1px solid #d7dde8;
  border-radius: 8px;
  min-height: 36px;
  padding: 0 10px;
}

.segmented-grid button.selected,
.topic-option-grid button.selected {
  background: #eff6ff;
  border-color: #2563eb;
  color: #1d4ed8;
}

.difficulty-control {
  display: grid;
  gap: 8px;
}

.difficulty-ratio-row span {
  background: #f8fafc;
  border: 1px solid #d7dde8;
  border-radius: 8px;
  padding: 6px 9px;
}

.goal-editor,
.goal-summary,
.learning-detail-area {
  display: grid;
  gap: 12px;
}

@media (max-width: 720px) {
  .plan-summary-card,
  .plan-list-row,
  .plan-row-actions,
  .modal-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .plan-summary-card .primary-button,
  .modal-actions .primary-button,
  .modal-actions .secondary-button {
    width: 100%;
  }

  .plan-row-actions {
    width: 100%;
  }
}
```

- [ ] **Step 2: Run frontend tests**

Run:

```bash
npm --cache ./.npm --prefix frontend test -- --run
```

Expected: all frontend tests pass.

- [ ] **Step 3: Run frontend build**

Run:

```bash
npm --cache ./.npm --prefix frontend run build
```

Expected: Vite build succeeds.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/styles.css frontend/src/styles.test.tsx
git commit -m "style: polish learning plan page layout"
```

---

## Task 8: Final Verification

**Files:**

- No code changes expected unless verification exposes a defect.

- [ ] **Step 1: Run backend tests**

Run:

```bash
make backend-test
```

Expected: Maven backend test suite passes.

- [ ] **Step 2: Run frontend tests**

Run:

```bash
make frontend-test
```

Expected: Vitest suite passes.

- [ ] **Step 3: Run full build**

Run:

```bash
make build
```

Expected: backend package and frontend build complete successfully.

- [ ] **Step 4: Inspect git diff**

Run:

```bash
git status --short
git log --oneline -8
```

Expected: only intentional changes are present, and commits correspond to the task commits above.

- [ ] **Step 5: Completion summary**

Prepare a concise summary with:

- Backend API changes: paginated `GET /api/learning-plans`, new `DELETE /api/learning-plans/{planId}`.
- Frontend changes: summary card, paginated list, delete action, create modal, difficulty distribution, editable goal summary.
- Verification commands and results.
