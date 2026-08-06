package org.congcong.algomentor.mentor.application.learningplan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LearningPlanServiceTest {

  private final InMemoryPlanRepository planRepository = new InMemoryPlanRepository();
  private final Clock clock = Clock.fixed(Instant.parse("2026-06-24T00:00:00Z"), ZoneOffset.UTC);
  private final LearningPlanService service = new LearningPlanService(
      planRepository,
      new LearningPlanLoadService(clock),
      clock);

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

  @Test
  void updateRhythmPersistsRhythmMetadataAndUpdatedAt() {
    planRepository.plans.put(900L, plan(900L, 42L, draftPlan()));

    LearningPlan updated = service.updateRhythm(42L, 900L, 3, 4);

    assertThat(updated.plan().metadata())
        .containsEntry("dailyProblemCount", 3)
        .containsEntry("trainingDaysPerWeek", 4)
        .containsEntry(LearningPlanDraftMetadataKeys.CONTENT_LOCALE, "en-US")
        .containsEntry(LearningPlanDraftMetadataKeys.PERSONALIZATION_ENABLED, true);
    assertThat(updated.plan().objective()).isEqualTo("Prepare for Java interviews");
    assertThat(updated.plan().difficultyDistribution()).isEqualTo(new LearningPlanDifficultyDistribution(35, 55, 10));
    assertThat(updated.plan().additionalConstraints()).isEqualTo("Reserve one weekly review session.");
    assertThat(updated.plan().phases()).hasSize(1);
    assertThat(updated.updatedAt()).isEqualTo(Instant.parse("2026-06-24T00:00:00Z"));
    assertThat(planRepository.plans.get(900L)).isSameAs(updated);
  }

  @Test
  void updateRhythmRejectsOutOfRangeValues() {
    planRepository.plans.put(900L, plan(900L, 42L, draftPlan()));

    assertThatThrownBy(() -> service.updateRhythm(42L, 900L, 11, 4))
        .isInstanceOf(LearningPlanException.class)
        .hasMessage("每天题目数必须在 1-10 之间。");
  }

  private static LearningPlan plan(long planId, long userId) {
    return plan(planId, userId, null);
  }

  private static LearningPlan plan(long planId, long userId, LearningPlanDraftPlan draftPlan) {
    Instant now = Instant.parse("2026-06-23T00:00:00Z");
    return new LearningPlan(planId, userId, LearningPlanStatus.ACTIVE, draftPlan, now, now);
  }

  private static LearningPlanDraftPlan draftPlan() {
    return new LearningPlanDraftPlan(
        "四周训练",
        "summary",
        LearningPlanIntent.INTERVIEW_SPRINT,
        "Prepare for Java interviews",
        4,
        LearningPlanLevel.INTERMEDIATE,
        6,
        "Java",
        new LearningPlanDifficultyDistribution(35, 55, 10),
        List.of("Array"),
        "Reserve one weekly review session.",
        List.of(new LearningPlanPhaseDraft(
            1,
            "基础阶段",
            4,
            "Array",
            List.of(new LearningPlanProblemDraft(
                "two-sum",
                1,
                "Two Sum",
                "两数之和",
                "EASY",
                List.of("Array"),
                "训练数组。",
                1)))),
        Map.of(
            "source", "test",
            LearningPlanDraftMetadataKeys.CONTENT_LOCALE, "en-US",
            LearningPlanDraftMetadataKeys.PERSONALIZATION_ENABLED, true));
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
