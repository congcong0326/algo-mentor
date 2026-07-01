package org.congcong.algomentor.mentor.application.learningplan;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LearningPlanDraftServiceTest {

  private final Clock clock = Clock.fixed(Instant.parse("2026-06-22T00:00:00Z"), ZoneOffset.UTC);
  private final InMemoryDraftRepository draftRepository = new InMemoryDraftRepository();
  private final InMemoryPlanRepository planRepository = new InMemoryPlanRepository();
  private final LearningPlanDraftService service = new LearningPlanDraftService(
      draftRepository,
      planRepository,
      new LearningPlanAgentService(new FakeProblemCatalog()),
      new LearningPlanDraftValidator(),
      clock);

  @Test
  void continueDraftCollectsMissingGoalAndGeneratesPlan() {
    LearningPlanDraft collecting = saveDraft(7L, new LearningPlanDraftCommand(
        LearningPlanIntent.PRACTICE_GOAL,
        "",
        2,
        LearningPlanLevel.BEGINNER,
        4,
        null,
        null,
        false,
        List.of()));

    LearningPlanDraftResult generated = service.continueDraft(7L, collecting.id(), "想用 Java 练习数组和哈希表");

    assertThat(generated.status()).isEqualTo(LearningPlanDraftStatus.GENERATED);
    assertThat(generated.draftPlan()).isNotNull();
    assertThat(generated.draftPlan().goal()).isEqualTo("想用 Java 练习数组和哈希表");
  }

  @Test
  void continueDraftRegeneratesPlanFromEditedGoalPrefix() {
    LearningPlanDraft original = saveGeneratedDraft(7L, completeCommand(4));

    LearningPlanDraftResult regenerated = service.continueDraft(
        7L,
        original.id(),
        "请按新的目标摘要重新生成学习计划：三周内集中突破动态规划面试题");

    assertThat(regenerated.status()).isEqualTo(LearningPlanDraftStatus.GENERATED);
    assertThat(regenerated.draftPlan()).isNotNull();
    assertThat(regenerated.draftPlan().goal()).isEqualTo("三周内集中突破动态规划面试题");
    assertThat(regenerated.draftPlan().summary()).contains("三周内集中突破动态规划面试题");
    assertThat(regenerated.draftPlan().phases())
        .flatExtracting(LearningPlanPhaseDraft::problems)
        .extracting(LearningPlanProblemDraft::reason)
        .allSatisfy(reason -> assertThat(reason).contains("三周内集中突破动态规划面试题"));
  }

  @Test
  void confirmDraftIsIdempotent() {
    LearningPlanDraft generated = saveGeneratedDraft(7L, completeCommand(8));

    LearningPlanConfirmResult first = service.confirmDraft(7L, generated.id());
    LearningPlanConfirmResult second = service.confirmDraft(7L, generated.id());

    assertThat(first.planId()).isEqualTo(second.planId());
    assertThat(first.status()).isEqualTo(LearningPlanStatus.ACTIVE);
    assertThat(draftRepository.findDraftByIdForUser(generated.id(), 7L).orElseThrow().status())
        .isEqualTo(LearningPlanDraftStatus.CONFIRMED);
  }

  @Test
  void confirmDraftRejectsCollectingDraft() {
    LearningPlanDraft collecting = saveDraft(7L, completeCommand(4));

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.confirmDraft(7L, collecting.id()))
        .isInstanceOf(LearningPlanException.class)
        .hasMessage("只有已生成的学习计划草案可以确认保存。");
  }

  private LearningPlanDraftCommand completeCommand(int durationWeeks) {
    return new LearningPlanDraftCommand(
        LearningPlanIntent.INTERVIEW_SPRINT,
        "准备 Java 后端算法面试",
        durationWeeks,
        LearningPlanLevel.INTERMEDIATE,
        6,
        "Java",
        LearningPlanDifficultyPreference.MEDIUM,
        true,
        List.of("Array", "Hash Table"));
  }

  private LearningPlanDraft saveDraft(long userId, LearningPlanDraftCommand command) {
    Instant now = clock.instant();
    return draftRepository.save(new LearningPlanDraft(
        null,
        userId,
        LearningPlanDraftStatus.COLLECTING,
        command,
        List.of(),
        List.of(),
        null,
        null,
        null,
        now.plus(14, ChronoUnit.DAYS),
        now,
        now));
  }

  private LearningPlanDraft saveGeneratedDraft(long userId, LearningPlanDraftCommand command) {
    LearningPlanDraft draft = saveDraft(userId, command);
    LearningPlanDraftResult generated = service.continueDraft(userId, draft.id(), "");
    return draftRepository.findDraftByIdForUser(generated.draftId(), userId).orElseThrow();
  }

  private static class FakeProblemCatalog implements LearningPlanProblemCatalog {

    @Override
    public List<LearningPlanProblemCandidate> searchProblems(LearningPlanProblemSearch search) {
      return List.of(
          new LearningPlanProblemCandidate("two-sum", 1, "Two Sum", "两数之和", "EASY", List.of("Array", "Hash Table")),
          new LearningPlanProblemCandidate(
              "valid-parentheses", 20, "Valid Parentheses", "有效的括号", "EASY", List.of("Stack")),
          new LearningPlanProblemCandidate(
              "binary-search", 704, "Binary Search", "二分查找", "EASY", List.of("Binary Search")),
          new LearningPlanProblemCandidate(
              "climbing-stairs", 70, "Climbing Stairs", "爬楼梯", "EASY", List.of("Dynamic Programming")));
    }

    @Override
    public Optional<LearningPlanProblemCandidate> findBySlug(String slug) {
      return searchProblems(new LearningPlanProblemSearch(null, null, 10)).stream()
          .filter(problem -> problem.slug().equals(slug))
          .findFirst();
    }
  }

  private static class InMemoryDraftRepository implements LearningPlanDraftRepository {

    private final Map<Long, LearningPlanDraft> drafts = new HashMap<>();
    private long sequence = 100;

    @Override
    public LearningPlanDraft save(LearningPlanDraft draft) {
      long id = draft.id() == null ? sequence++ : draft.id();
      LearningPlanDraft saved = draft.withId(id);
      drafts.put(id, saved);
      return saved;
    }

    @Override
    public Optional<LearningPlanDraft> findDraftByIdForUser(long draftId, long userId) {
      return Optional.ofNullable(drafts.get(draftId)).filter(draft -> draft.userId() == userId);
    }
  }

  private static class InMemoryPlanRepository implements LearningPlanRepository {

    private final Map<Long, LearningPlan> plans = new HashMap<>();
    private long sequence = 900;

    @Override
    public LearningPlan save(LearningPlan plan) {
      long id = plan.id() == null ? sequence++ : plan.id();
      LearningPlan saved = plan.withId(id);
      plans.put(id, saved);
      return saved;
    }

    @Override
    public List<LearningPlan> findByUserId(long userId) {
      return new ArrayList<>(plans.values()).stream()
          .filter(plan -> plan.userId() == userId)
          .toList();
    }

    @Override
    public Optional<LearningPlan> findPlanByIdForUser(long planId, long userId) {
      return Optional.ofNullable(plans.get(planId)).filter(plan -> plan.userId() == userId);
    }
  }
}
