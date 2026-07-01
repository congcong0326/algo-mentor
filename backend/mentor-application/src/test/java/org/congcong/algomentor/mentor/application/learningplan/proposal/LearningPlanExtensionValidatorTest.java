package org.congcong.algomentor.mentor.application.learningplan.proposal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDifficultyPreference;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanIntent;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLevel;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanPhaseDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemCandidate;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemCatalog;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemSearch;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanStatus;
import org.junit.jupiter.api.Test;

class LearningPlanExtensionValidatorTest {

  private final LearningPlanExtensionValidator validator = new LearningPlanExtensionValidator(new FakeProblemCatalog());

  @Test
  void rejectsEmptyNewPhases() {
    assertThatThrownBy(() -> validator.validate(new LearningPlanExtensionDraft("补充图论", List.of(), null), plan(), List.of()))
        .isInstanceOf(LearningPlanException.class)
        .hasMessage("扩展提案至少需要一个新增阶段。")
        .extracting("code")
        .isEqualTo("LEARNING_PLAN_EXTENSION_INVALID");
  }

  @Test
  void rejectsExistingProblemSlug() {
    LearningPlanExtensionDraft extension = new LearningPlanExtensionDraft(
        "补充图论",
        List.of(phase(2, "two-sum")),
        null);

    assertThatThrownBy(() -> validator.validate(extension, plan(), List.of()))
        .isInstanceOf(LearningPlanException.class)
        .hasMessage("扩展提案不能重复推荐已有计划题目。");
  }

  @Test
  void rejectsUnknownProblemSlug() {
    LearningPlanExtensionDraft extension = new LearningPlanExtensionDraft(
        "补充图论",
        List.of(phase(2, "unknown-problem")),
        null);

    assertThatThrownBy(() -> validator.validate(extension, plan(), List.of()))
        .isInstanceOf(LearningPlanException.class)
        .hasMessage("扩展提案包含本地题库不存在的题目。");
  }

  @Test
  void rejectsNonIncreasingPhaseIndex() {
    LearningPlanExtensionDraft extension = new LearningPlanExtensionDraft(
        "补充图论",
        List.of(phase(3, "graph-valid-tree"), phase(2, "number-of-islands")),
        null);

    assertThatThrownBy(() -> validator.validate(extension, plan(), List.of()))
        .isInstanceOf(LearningPlanException.class)
        .hasMessage("扩展阶段编号必须追加在当前计划之后并递增。");
  }

  @Test
  void rejectsDuplicateSlugInsideExtension() {
    LearningPlanExtensionDraft extension = new LearningPlanExtensionDraft(
        "补充图论",
        List.of(phase(2, "graph-valid-tree"), phase(3, "graph-valid-tree")),
        null);

    assertThatThrownBy(() -> validator.validate(extension, plan(), List.of()))
        .isInstanceOf(LearningPlanException.class)
        .hasMessage("扩展提案内部不能重复推荐同一道题。");
  }

  @Test
  void rejectsMoreThanFiveProblemsInOneExtensionPhase() {
    LearningPlanExtensionDraft extension = new LearningPlanExtensionDraft(
        "补充图论",
        List.of(phase(
            2,
            "problem-1",
            "problem-2",
            "problem-3",
            "problem-4",
            "problem-5",
            "problem-6")),
        null);

    assertThatThrownBy(() -> validator.validate(extension, plan(), List.of()))
        .isInstanceOf(LearningPlanException.class)
        .hasMessage("每个扩展阶段最多推荐 5 道题。")
        .extracting("code")
        .isEqualTo("LEARNING_PLAN_EXTENSION_INVALID");
  }

  private static LearningPlan plan() {
    Instant now = Instant.parse("2026-07-01T00:00:00Z");
    return new LearningPlan(
        12L,
        7L,
        LearningPlanStatus.ACTIVE,
        draftPlan(List.of(phase(1, "two-sum"))),
        now,
        now);
  }

  private static LearningPlanDraftPlan draftPlan(List<LearningPlanPhaseDraft> phases) {
    return new LearningPlanDraftPlan(
        "学习计划",
        "基础训练",
        LearningPlanIntent.INTERVIEW_SPRINT,
        "准备算法面试",
        4,
        LearningPlanLevel.INTERMEDIATE,
        6,
        "Java",
        LearningPlanDifficultyPreference.MEDIUM,
        true,
        List.of("Graph"),
        "已有基础",
        phases,
        Map.of());
  }

  private static LearningPlanPhaseDraft phase(int phaseIndex, String... slugs) {
    List<LearningPlanProblemDraft> problems = java.util.stream.IntStream.range(0, slugs.length)
        .mapToObj(index -> new LearningPlanProblemDraft(
            slugs[index],
            index + 1,
            slugs[index],
            slugs[index],
            "MEDIUM",
            List.of("Graph"),
            "练习",
            index + 1))
        .toList();
    return new LearningPlanPhaseDraft(
        phaseIndex,
        "阶段 " + phaseIndex,
        1,
        "图论",
        List.of("掌握图论"),
        List.of("Graph"),
        List.of("完成练习"),
        "复盘模板",
        problems);
  }

  private static class FakeProblemCatalog implements LearningPlanProblemCatalog {

    @Override
    public List<LearningPlanProblemCandidate> searchProblems(LearningPlanProblemSearch search) {
      return List.of(
          candidate("graph-valid-tree"),
          candidate("number-of-islands"));
    }

    @Override
    public Optional<LearningPlanProblemCandidate> findBySlug(String slug) {
      return searchProblems(new LearningPlanProblemSearch(null, null, 10)).stream()
          .filter(candidate -> candidate.slug().equals(slug))
          .findFirst();
    }

    private static LearningPlanProblemCandidate candidate(String slug) {
      return new LearningPlanProblemCandidate(slug, 1, slug, slug, "MEDIUM", List.of("Graph"));
    }
  }
}
