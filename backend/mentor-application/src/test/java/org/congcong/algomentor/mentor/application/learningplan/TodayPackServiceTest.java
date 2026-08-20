package org.congcong.algomentor.mentor.application.learningplan;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.congcong.algomentor.mentor.application.practice.PracticeProgress;
import org.congcong.algomentor.mentor.application.practice.PracticeProgressStatus;
import org.congcong.algomentor.mentor.application.practice.PracticeSession;
import org.congcong.algomentor.mentor.application.practice.PracticeSessionRepository;
import org.junit.jupiter.api.Test;

class TodayPackServiceTest {

  private static final long USER_ID = 42L;
  private static final long PLAN_ID = 900L;
  private static final Instant ACTIVATED_AT = Instant.parse("2026-08-19T00:00:00Z");
  private static final Clock CLOCK = Clock.fixed(ACTIVATED_AT.plusSeconds(3_600), ZoneOffset.UTC);

  @Test
  void restartMakesNextOpenProblemsAvailableTodayAfterKeepingHistoricalCompletions() {
    LearningPlan plan = plan();
    LearningPlanActivation selection = new LearningPlanActivation(
        USER_ID, PLAN_ID, ACTIVATED_AT, ACTIVATED_AT, ACTIVATED_AT);
    List<PracticeProgress> progress = List.of(
        progress("first", PracticeProgressStatus.COMPLETED, ACTIVATED_AT.minusSeconds(7_200)),
        progress("second", PracticeProgressStatus.SKIPPED, ACTIVATED_AT.minusSeconds(3_600)));

    TodayPackService service = new TodayPackService(
        activationService(selection),
        new StubPlanRepository(plan),
        new StubPracticeSessionRepository(progress),
        new LearningPlanLoadService(CLOCK),
        CLOCK);

    TodayPack pack = service.getTodayPack(USER_ID, "UTC", 0);

    assertThat(pack.state()).isEqualTo(TodayPackState.READY);
    assertThat(pack.sections()).hasSize(1);
    assertThat(pack.sections().get(0).type()).isEqualTo(TodayPackSectionType.TODAY);
    assertThat(pack.sections().get(0).problems())
        .extracting(TodayPackProblem::slug)
        .containsExactly("third", "fourth");
    assertThat(pack.sections().get(0).problems())
        .allMatch(problem -> problem.scheduledDate().equals(pack.localDate()));
  }

  @Test
  void homeSummaryCountsDueProblemsWithoutBuildingProblemSections() {
    LearningPlanActivation selection = new LearningPlanActivation(
        USER_ID, PLAN_ID, ACTIVATED_AT, ACTIVATED_AT, ACTIVATED_AT);
    TodayPackService service = new TodayPackService(
        activationService(selection),
        new StubPlanRepository(plan()),
        new StubPracticeSessionRepository(List.of(
            progress("first", PracticeProgressStatus.COMPLETED, ACTIVATED_AT.plusSeconds(600)))),
        new LearningPlanLoadService(CLOCK),
        CLOCK);

    TodayPackHomeSummary summary = service.getHomeSummary(USER_ID, "UTC");

    assertThat(summary.state()).isEqualTo(TodayPackState.READY);
    assertThat(summary.dueProblemCount()).isEqualTo(1);
    assertThat(summary.activePlan()).extracting(TodayPackHomeActivePlan::planId).isEqualTo(PLAN_ID);
    assertThat(summary.nextPackDate()).isEqualTo(ACTIVATED_AT.atZone(ZoneOffset.UTC).toLocalDate().plusDays(1));
  }

  @Test
  void planWorkspaceReusesTheActivePlanSnapshotForPackAndProgressContext() {
    LearningPlan plan = plan();
    LearningPlanActivation selection = new LearningPlanActivation(
        USER_ID, PLAN_ID, ACTIVATED_AT, ACTIVATED_AT, ACTIVATED_AT);
    List<PracticeProgress> progress = List.of(
        progress("first", PracticeProgressStatus.COMPLETED, ACTIVATED_AT.plusSeconds(600)));
    TodayPackService service = new TodayPackService(
        activationService(selection),
        new StubPlanRepository(plan),
        new StubPracticeSessionRepository(progress),
        new LearningPlanLoadService(CLOCK),
        CLOCK);

    TodayPackWorkspace workspace = service.getPlanWorkspace(USER_ID, PLAN_ID, "UTC", 0).orElseThrow();

    assertThat(workspace.plan()).isSameAs(plan);
    assertThat(workspace.progress()).containsExactlyElementsOf(progress);
    assertThat(workspace.pack().activePlan()).extracting(TodayPackActivePlan::planId).isEqualTo(PLAN_ID);
    assertThat(service.getPlanWorkspace(USER_ID, PLAN_ID + 1, "UTC", 0)).isEmpty();
  }

  private static LearningPlanActivationService activationService(LearningPlanActivation selection) {
    return new LearningPlanActivationService(null, null, CLOCK) {
      @Override
      public Optional<LearningPlanActivation> findActiveSelection(long userId) {
        return Optional.of(selection);
      }
    };
  }

  private static LearningPlan plan() {
    List<LearningPlanProblemDraft> problems = List.of(
        problem("first", 1),
        problem("second", 2),
        problem("third", 3),
        problem("fourth", 4));
    LearningPlanDraftPlan draft = new LearningPlanDraftPlan(
        "test plan",
        "summary",
        LearningPlanIntent.INTERVIEW_SPRINT,
        "practice",
        2,
        LearningPlanLevel.INTERMEDIATE,
        4,
        "Java",
        new LearningPlanDifficultyDistribution(25, 50, 25),
        List.of("Array"),
        null,
        List.of(new LearningPlanPhaseDraft(1, "phase", 2, "focus", problems)),
        Map.of(
            LearningPlanDraftMetadataKeys.DAILY_PROBLEM_COUNT, 2,
            LearningPlanDraftMetadataKeys.TRAINING_DAYS_PER_WEEK, 5));
    return new LearningPlan(
        PLAN_ID, USER_ID, LearningPlanStatus.ACTIVE, draft, ACTIVATED_AT, ACTIVATED_AT);
  }

  private static LearningPlanProblemDraft problem(String slug, int sortOrder) {
    return new LearningPlanProblemDraft(slug, sortOrder, slug, slug, "EASY", List.of("Array"), null, sortOrder);
  }

  private static PracticeProgress progress(String slug, PracticeProgressStatus status, Instant terminalAt) {
    return new PracticeProgress(
        slug.hashCode() & Integer.MAX_VALUE,
        USER_ID,
        PLAN_ID,
        1,
        slug,
        status,
        terminalAt,
        status == PracticeProgressStatus.COMPLETED ? terminalAt : null,
        status == PracticeProgressStatus.SKIPPED ? terminalAt : null,
        terminalAt,
        terminalAt);
  }

  private static final class StubPlanRepository implements LearningPlanRepository {
    private final LearningPlan plan;

    private StubPlanRepository(LearningPlan plan) {
      this.plan = plan;
    }

    @Override
    public LearningPlan save(LearningPlan plan) {
      return plan;
    }

    @Override
    public List<LearningPlan> findByUserId(long userId) {
      return List.of(plan);
    }

    @Override
    public Optional<LearningPlan> findPlanByIdForUser(long planId, long userId) {
      return planId == plan.id() && userId == plan.userId() ? Optional.of(plan) : Optional.empty();
    }
  }

  private static final class StubPracticeSessionRepository implements PracticeSessionRepository {
    private final List<PracticeProgress> progress;

    private StubPracticeSessionRepository(List<PracticeProgress> progress) {
      this.progress = progress;
    }

    @Override
    public PracticeProgress upsertAndAdvanceProgress(long userId, long planId, int phaseIndex, String problemSlug) {
      throw new UnsupportedOperationException();
    }

    @Override
    public PracticeSession upsertAndLockSession(
        long userId, long planId, int phaseIndex, String problemSlug, String locale) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<PracticeSession> findSessionForUser(long sessionId, long userId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public PracticeSession attachAgentTask(long sessionId, long agentTaskId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public PracticeSession attachProblemStatementMessage(long sessionId, long messageId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public PracticeProgress updateProgressStatus(long sessionId, long userId, PracticeProgressStatus status) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<PracticeProgress> findProgressByPlan(long userId, long planId) {
      return progress;
    }

    @Override
    public void touchLastMessageAt(long sessionId) {
      throw new UnsupportedOperationException();
    }
  }
}
