package org.congcong.algomentor.mentor.application.learningplan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LearningPlanServiceTest {

  private final InMemoryPlanRepository planRepository = new InMemoryPlanRepository();
  private final LearningPlanService service = new LearningPlanService(planRepository);

  @Test
  void listPlansNormalizesPageAndPageSize() {
    LearningPlanPage expectedPage = new LearningPlanPage(
        List.of(plan(900L, 42L)),
        1,
        1,
        50,
        1,
        0,
        Instant.parse("2026-06-23T00:00:00Z"));
    planRepository.page = expectedPage;

    LearningPlanPage actualPage = service.listPlans(42L, 0, 500);

    assertThat(actualPage).isSameAs(expectedPage);
    assertThat(planRepository.lastPageUserId).isEqualTo(42L);
    assertThat(planRepository.lastPage).isEqualTo(1);
    assertThat(planRepository.lastPageSize).isEqualTo(50);
  }

  @Test
  void deletePlanClearsDraftReferenceAndDeletesOwnedPlan() {
    planRepository.plans.put(900L, plan(900L, 42L));

    service.deletePlan(42L, 900L);

    assertThat(planRepository.clearedReferences).containsExactly("42:900");
    assertThat(planRepository.deletedPlans).containsExactly("42:900");
    assertThat(planRepository.plans).doesNotContainKey(900L);
  }

  @Test
  void deletePlanThrowsWhenPlanIsMissing() {
    assertThatThrownBy(() -> service.deletePlan(42L, 900L))
        .isInstanceOf(LearningPlanException.class)
        .hasMessage("学习计划不存在。");

    assertThat(planRepository.clearedReferences).isEmpty();
    assertThat(planRepository.deletedPlans).isEmpty();
  }

  @Test
  void deletePlanThrowsWhenCombinedDeleteReturnsFalseAfterPlanLookup() {
    planRepository.plans.put(900L, plan(900L, 42L));
    planRepository.deleteResult = false;

    assertThatThrownBy(() -> service.deletePlan(42L, 900L))
        .isInstanceOf(LearningPlanException.class)
        .hasMessage("学习计划不存在。");

    assertThat(planRepository.clearedReferences).containsExactly("42:900");
    assertThat(planRepository.deletedPlans).containsExactly("42:900");
  }

  private static LearningPlan plan(long planId, long userId) {
    Instant now = Instant.parse("2026-06-23T00:00:00Z");
    return new LearningPlan(planId, userId, LearningPlanStatus.ACTIVE, null, now, now);
  }

  private static class InMemoryPlanRepository implements LearningPlanRepository {

    private final Map<Long, LearningPlan> plans = new HashMap<>();
    private final List<String> clearedReferences = new ArrayList<>();
    private final List<String> deletedPlans = new ArrayList<>();
    private LearningPlanPage page;
    private long lastPageUserId;
    private int lastPage;
    private int lastPageSize;
    private boolean deleteResult = true;

    @Override
    public LearningPlan save(LearningPlan plan) {
      plans.put(plan.id(), plan);
      return plan;
    }

    @Override
    public List<LearningPlan> findByUserId(long userId) {
      return plans.values().stream()
          .filter(plan -> plan.userId() == userId)
          .toList();
    }

    @Override
    public Optional<LearningPlan> findPlanByIdForUser(long planId, long userId) {
      return Optional.ofNullable(plans.get(planId)).filter(plan -> plan.userId() == userId);
    }

    @Override
    public LearningPlanPage findPageByUserId(long userId, int page, int pageSize) {
      lastPageUserId = userId;
      lastPage = page;
      lastPageSize = pageSize;
      return this.page;
    }

    @Override
    public void clearConfirmedPlanReferences(long userId, long planId) {
      clearedReferences.add(userId + ":" + planId);
    }

    @Override
    public boolean deletePlanByIdForUser(long planId, long userId) {
      deletedPlans.add(userId + ":" + planId);
      return plans.remove(planId) != null;
    }

    @Override
    public boolean deletePlanAndClearReferences(long userId, long planId) {
      clearConfirmedPlanReferences(userId, planId);
      deletedPlans.add(userId + ":" + planId);
      if (!deleteResult) {
        return false;
      }
      return plans.remove(planId) != null;
    }
  }
}
