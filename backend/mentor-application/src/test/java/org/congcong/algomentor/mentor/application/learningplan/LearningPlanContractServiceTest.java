package org.congcong.algomentor.mentor.application.learningplan;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.congcong.algomentor.mentor.application.practice.PracticeProgress;
import org.congcong.algomentor.mentor.application.practice.PracticeProgressStatus;
import org.junit.jupiter.api.Test;

class LearningPlanContractServiceTest {

  private final Clock clock = Clock.fixed(Instant.parse("2026-07-15T00:00:00Z"), ZoneOffset.UTC);
  private final LearningPlanContractService service = new LearningPlanContractService(clock, new LearningPlanLoadService(clock));

  @Test
  void usesPlanQuotaBeforeThreeCompletedProblems() {
    LearningPlanLivingContractSummary summary = service.summarize(plan(), List.of());

    assertThat(summary.totalProblemCount()).isEqualTo(5);
    assertThat(summary.estimatedCompletionDate()).isEqualTo(LocalDate.parse("2026-07-29"));
    assertThat(summary.estimationSource()).isEqualTo(LearningPlanEstimationSource.COLD_START_PLAN_QUOTA);
    assertThat(summary.visibleStatus()).isEqualTo(LearningPlanVisibleStatus.ON_TRACK);
  }

  @Test
  void switchesToRecentCompletionRateAfterThreeCompletions() {
    LearningPlanLivingContractSummary summary = service.summarize(plan(), List.of(
        progress(1, "two-sum", PracticeProgressStatus.COMPLETED, "2026-07-13T00:00:00Z"),
        progress(1, "valid-parentheses", PracticeProgressStatus.COMPLETED, "2026-07-14T00:00:00Z"),
        progress(2, "binary-search", PracticeProgressStatus.COMPLETED, "2026-07-15T00:00:00Z")));

    assertThat(summary.completedProblemCount()).isEqualTo(3);
    assertThat(summary.openProblemCount()).isEqualTo(2);
    assertThat(summary.estimationSource()).isEqualTo(LearningPlanEstimationSource.RECENT_COMPLETION_RATE);
    assertThat(summary.estimatedCompletionDate()).isEqualTo(LocalDate.parse("2026-07-20"));
  }

  @Test
  void skippedProblemsAreCountedButNotReturnedInNextTrainingPackage() {
    LearningPlanLivingContractSummary summary = service.summarize(plan(), List.of(
        progress(1, "two-sum", PracticeProgressStatus.COMPLETED, "2026-07-13T00:00:00Z"),
        progress(1, "valid-parentheses", PracticeProgressStatus.SKIPPED, "2026-07-14T00:00:00Z")));

    assertThat(summary.skippedProblemCount()).isEqualTo(1);
    assertThat(summary.nextTrainingPackage().priorityProblemSlugs())
        .doesNotContain("two-sum", "valid-parentheses")
        .contains("binary-search");
  }

  @Test
  void derivesPausedStatusAfterSevenIdleDays() {
    LearningPlanLivingContractSummary summary = service.summarize(plan(), List.of(
        progress(1, "two-sum", PracticeProgressStatus.COMPLETED, "2026-07-07T00:00:00Z")));

    assertThat(summary.visibleStatus()).isEqualTo(LearningPlanVisibleStatus.PAUSED);
    assertThat(summary.notice()).contains("连续 7 天");
  }

  @Test
  void returnsCompletionSummaryWhenAllProblemsCompleted() {
    LearningPlanLivingContractSummary summary = service.summarize(plan(), List.of(
        progress(1, "two-sum", PracticeProgressStatus.COMPLETED, "2026-07-11T00:00:00Z"),
        progress(1, "valid-parentheses", PracticeProgressStatus.COMPLETED, "2026-07-12T00:00:00Z"),
        progress(2, "binary-search", PracticeProgressStatus.COMPLETED, "2026-07-13T00:00:00Z"),
        progress(2, "climbing-stairs", PracticeProgressStatus.COMPLETED, "2026-07-14T00:00:00Z"),
        progress(2, "coin-change", PracticeProgressStatus.COMPLETED, "2026-07-15T00:00:00Z")));

    assertThat(summary.visibleStatus()).isEqualTo(LearningPlanVisibleStatus.COMPLETED);
    assertThat(summary.estimationSource()).isEqualTo(LearningPlanEstimationSource.COMPLETED);
    assertThat(summary.completionSummary()).isNotNull();
    assertThat(summary.completionSummary().completedProblemCount()).isEqualTo(5);
    assertThat(summary.completionSummary().strongTags()).contains("array", "dp");
  }

  private LearningPlan plan() {
    return new LearningPlan(
        900L,
        7L,
        LearningPlanStatus.ACTIVE,
        new LearningPlanDraftPlan(
            "route",
            "summary",
            LearningPlanIntent.INTERVIEW_SPRINT,
            "objective fixture",
            2,
            LearningPlanLevel.INTERMEDIATE,
            5,
            "Java",
            new org.congcong.algomentor.mentor.application.learningplan.LearningPlanDifficultyDistribution(25, 55, 20),
            List.of("array", "dp"),
            "profile",
            List.of(
                phase(1, "基础", List.of(
                    problem("two-sum", "EASY", List.of("Array")),
                    problem("valid-parentheses", "EASY", List.of("Stack")))),
                phase(2, "强化", List.of(
                    problem("binary-search", "EASY", List.of("Binary Search")),
                    problem("climbing-stairs", "MEDIUM", List.of("DP")),
                    problem("coin-change", "MEDIUM", List.of("DP"))))),
            Map.of()),
        Instant.parse("2026-07-15T00:00:00Z"),
        Instant.parse("2026-07-15T00:00:00Z"));
  }

  private LearningPlanPhaseDraft phase(int phaseIndex, String title, List<LearningPlanProblemDraft> problems) {
    return new LearningPlanPhaseDraft(
        phaseIndex,
        title,
        1,
        "focus",
        problems);
  }

  private LearningPlanProblemDraft problem(String slug, String difficulty, List<String> tags) {
    return new LearningPlanProblemDraft(slug, 1, slug, slug, difficulty, tags, "reason", 1);
  }

  private PracticeProgress progress(
      int phaseIndex,
      String slug,
      PracticeProgressStatus status,
      String timestamp
  ) {
    Instant time = Instant.parse(timestamp);
    return new PracticeProgress(
        phaseIndex * 100L + slug.length(),
        7L,
        900L,
        phaseIndex,
        slug,
        status,
        time,
        status == PracticeProgressStatus.COMPLETED ? time : null,
        status == PracticeProgressStatus.SKIPPED ? time : null,
        time,
        time);
  }
}
