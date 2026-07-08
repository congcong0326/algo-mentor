package org.congcong.algomentor.mentor.application.learningplan;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.congcong.algomentor.mentor.application.learningplan.template.LearningPlanTemplate;
import org.congcong.algomentor.mentor.application.learningplan.template.LearningPlanTemplatePhase;
import org.congcong.algomentor.mentor.application.learningplan.template.LearningPlanTemplateProblemRef;
import org.congcong.algomentor.mentor.application.practice.PracticeProgress;
import org.congcong.algomentor.mentor.application.practice.PracticeProgressStatus;
import org.junit.jupiter.api.Test;

class LearningPlanLoadServiceTest {

  private final Clock clock = Clock.fixed(Instant.parse("2026-07-15T00:00:00Z"), ZoneOffset.UTC);
  private final LearningPlanLoadService service = new LearningPlanLoadService(clock);

  @Test
  void estimatesLoadSummaryWithDifficultyTopicBonusAndReviewBuffer() {
    LearningPlanLoadSummary summary = service.summarize(plan(2, 3), LearningPlanCoveragePolicy.FULL_ROUTE_WITH_REVIEW_BUFFER);

    assertThat(summary.plannedProblemCount()).isEqualTo(2);
    assertThat(summary.plannedLoadPoints()).isEqualTo(4.5D);
    assertThat(summary.totalCapacityPoints()).isEqualTo(6D);
    assertThat(summary.loadRatio()).isEqualTo(0.75D);
    assertThat(summary.intensity()).isEqualTo("RELAXED");
    assertThat(summary.reviewBufferIncluded()).isTrue();
  }

  @Test
  void computesDefaultTemplateRhythmWithoutChangingTemplateDuration() {
    LearningPlanTemplate template = template();

    LearningPlanRhythmSettings rhythm = service.defaultRhythmSettings(template);
    LearningPlanLoadSummary loadSummary = service.defaultLoadSummary(template);

    assertThat(rhythm.dailyProblemCount()).isEqualTo(1);
    assertThat(rhythm.trainingDaysPerWeek()).isEqualTo(5);
    assertThat(rhythm.totalProblemCount()).isEqualTo(2);
    assertThat(rhythm.remainingProblemCount()).isEqualTo(2);
    assertThat(rhythm.estimatedRemainingWeeks()).isEqualTo(1);
    assertThat(loadSummary.durationWeeks()).isEqualTo(4);
    assertThat(loadSummary.weeklyHours()).isEqualTo(6);
    assertThat(loadSummary.plannedProblemCount()).isEqualTo(2);
  }

  @Test
  void splitsWeeklyBucketsByPhaseOrderAndComputesPace() {
    LearningPlanDraftPlan draftPlan = service.withLoadMetadata(
        plan(4, 3),
        LearningPlanCoveragePolicy.FIT_USER_BUDGET);
    LearningPlan plan = new LearningPlan(
        900L,
        7L,
        LearningPlanStatus.ACTIVE,
        draftPlan,
        Instant.parse("2026-07-01T00:00:00Z"),
        Instant.parse("2026-07-01T00:00:00Z"));

    List<LearningPlanWeeklyBucket> buckets = service.weeklyBuckets(draftPlan);
    LearningPlanPaceSummary pace = service.paceSummary(plan, List.of(
        progress("two-sum", PracticeProgressStatus.COMPLETED)), clock.instant());

    assertThat(buckets).hasSize(4);
    assertThat(buckets.get(0).problemSlugs()).containsExactly("two-sum");
    assertThat(buckets.get(1).problemSlugs()).containsExactly("number-of-islands");
    assertThat(pace.currentWeek()).isEqualTo(3);
    assertThat(pace.plannedProblemCountToDate()).isEqualTo(2);
    assertThat(pace.completedProblemCountToDate()).isEqualTo(1);
    assertThat(pace.status()).isEqualTo(LearningPlanPaceStatus.AT_RISK);
    assertThat(draftPlan.metadata())
        .containsEntry("dailyProblemCount", 1)
        .containsEntry("trainingDaysPerWeek", 5)
        .doesNotContainKeys("nextTrainingPackage", "weeklyBuckets", "rhythmMode");
  }

  @Test
  void computesRhythmSettingsAndNextPackageFromCurrentProgress() {
    LearningPlanDraftPlan draftPlan = service.withRhythmMetadata(plan(4, 3), 2, 3);
    LearningPlan plan = new LearningPlan(
        900L,
        7L,
        LearningPlanStatus.ACTIVE,
        draftPlan,
        Instant.parse("2026-07-01T00:00:00Z"),
        Instant.parse("2026-07-01T00:00:00Z"));

    List<PracticeProgress> progress = List.of(
        progress("two-sum", PracticeProgressStatus.COMPLETED));
    LearningPlanRhythmSettings settings = service.rhythmSettings(draftPlan, progress);
    LearningPlanTrainingPackage trainingPackage = service.nextTrainingPackage(plan, progress);

    assertThat(settings.dailyProblemCount()).isEqualTo(2);
    assertThat(settings.trainingDaysPerWeek()).isEqualTo(3);
    assertThat(settings.totalProblemCount()).isEqualTo(2);
    assertThat(settings.completedProblemCount()).isEqualTo(1);
    assertThat(settings.remainingProblemCount()).isEqualTo(1);
    assertThat(settings.estimatedRemainingWeeks()).isEqualTo(1);
    assertThat(trainingPackage.newProblemCount()).isEqualTo(1);
    assertThat(trainingPackage.priorityProblemSlugs()).containsExactly("number-of-islands");
  }

  private LearningPlanDraftPlan plan(int durationWeeks, int weeklyHours) {
    return new LearningPlanDraftPlan(
        "四周训练",
        "summary",
        LearningPlanIntent.INTERVIEW_SPRINT,
        "准备算法面试",
        durationWeeks,
        LearningPlanLevel.INTERMEDIATE,
        weeklyHours,
        "Java",
        LearningPlanDifficultyPreference.MEDIUM,
        true,
        List.of("Array", "Graph"),
        "profile",
        List.of(new LearningPlanPhaseDraft(
            1,
            "基础阶段",
            durationWeeks,
            "Array and Graph",
            List.of("完成基础训练"),
            List.of("Array", "Graph"),
            List.of("能复盘"),
            "记录错题。",
            List.of(
                problem("two-sum", "EASY", List.of("Array")),
                problem("number-of-islands", "MEDIUM", List.of("Graph"))))),
        Map.of());
  }

  private LearningPlanProblemDraft problem(String slug, String difficulty, List<String> tags) {
    return new LearningPlanProblemDraft(
        slug,
        1,
        slug,
        slug,
        difficulty,
        tags,
        "训练题。",
        1);
  }

  private PracticeProgress progress(String slug, PracticeProgressStatus status) {
    return new PracticeProgress(
        10L + slug.length(),
        7L,
        900L,
        1,
        slug,
        status,
        Instant.parse("2026-07-01T00:00:00Z"),
        status == PracticeProgressStatus.COMPLETED ? Instant.parse("2026-07-01T00:00:00Z") : null,
        status == PracticeProgressStatus.SKIPPED ? Instant.parse("2026-07-01T00:00:00Z") : null,
        Instant.parse("2026-07-01T00:00:00Z"),
        Instant.parse("2026-07-01T00:00:00Z"));
  }

  private LearningPlanTemplate template() {
    return new LearningPlanTemplate(
        null,
        "template",
        "模板",
        "summary",
        LearningPlanIntent.INTERVIEW_SPRINT,
        "准备算法面试",
        4,
        LearningPlanLevel.INTERMEDIATE,
        6,
        "Java",
        LearningPlanDifficultyPreference.MEDIUM,
        true,
        List.of("Array", "Graph"),
        "准备面试",
        Map.of(),
        List.of(),
        List.of(),
        List.of(),
        "完成训练",
        "source",
        "https://example.com",
        "commit",
        "seed.jsonl",
        "source",
        "notes",
        "license",
        2,
        2,
        0,
        Map.of(),
        List.of(templatePhase(1, "数组", "two-sum", "Easy"), templatePhase(2, "图", "number-of-islands", "Medium")));
  }

  private LearningPlanTemplatePhase templatePhase(int phaseIndex, String pattern, String slug, String difficulty) {
    return new LearningPlanTemplatePhase(
        null,
        phaseIndex,
        pattern,
        1,
        pattern,
        List.of(pattern),
        List.of(pattern),
        List.of("能复盘"),
        "记录错题。",
        List.of(new LearningPlanTemplateProblemRef(
            null,
            phaseIndex,
            1,
            phaseIndex,
            slug,
            slug,
            difficulty,
            pattern,
            "https://example.com/" + slug,
            true,
            Map.of())));
  }
}
