package org.congcong.algomentor.mentor.application.learningplan;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.DateTimeException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.congcong.algomentor.mentor.application.practice.PracticeProgress;
import org.congcong.algomentor.mentor.application.practice.PracticeProgressStatus;
import org.congcong.algomentor.mentor.application.practice.PracticeSessionRepository;

public class TodayPackService {

  public static final String RECOMMENDED_TEMPLATE_ID = "neetcode_blind_75_interview_core";
  public static final int CARRYOVER_NOTICE_THRESHOLD_DAYS = 7;

  private final LearningPlanActivationService activationService;
  private final LearningPlanRepository planRepository;
  private final PracticeSessionRepository practiceSessionRepository;
  private final LearningPlanLoadService loadService;
  private final Clock clock;

  public TodayPackService(
      LearningPlanActivationService activationService,
      LearningPlanRepository planRepository,
      PracticeSessionRepository practiceSessionRepository,
      LearningPlanLoadService loadService,
      Clock clock) {
    this.activationService = activationService;
    this.planRepository = planRepository;
    this.practiceSessionRepository = practiceSessionRepository;
    this.loadService = loadService == null ? new LearningPlanLoadService() : loadService;
    this.clock = clock == null ? Clock.systemUTC() : clock;
  }

  public TodayPack getTodayPack(long userId, String timezone, Integer packOffset) {
    ZoneId zoneId = zoneId(timezone);
    int offset = Math.max(0, packOffset == null ? 0 : packOffset);
    LocalDate today = LocalDate.now(clock.withZone(zoneId));
    return activationService.findActiveSelection(userId)
        .map(selection -> packForSelection(userId, selection, zoneId, today, offset))
        .orElseGet(() -> noActivePack(zoneId, today, offset));
  }

  private TodayPack packForSelection(
      long userId,
      LearningPlanActivation selection,
      ZoneId zoneId,
      LocalDate today,
      int packOffset) {
    LearningPlan plan = planRepository.findPlanByIdForUser(selection.planId(), userId)
        .orElseThrow(() -> new LearningPlanException("LEARNING_PLAN_NOT_FOUND", "学习计划不存在。"));
    List<PracticeProgress> progress = progressByPlan(userId, plan.id());
    Map<ProblemKey, PracticeProgress> progressByProblem = progressByProblem(progress);
    LearningPlanRhythmSettings rhythm = loadService.rhythmSettings(plan.plan(), progress);
    int dailyProblemCount = Math.max(1, rhythm.dailyProblemCount());
    LocalDate activatedLocalDate = selection.activatedAt().atZone(zoneId).toLocalDate();
    LocalDate displayDate = today.plusDays(packOffset);

    List<ScheduledProblem> scheduled = scheduledProblems(
        plan, progressByProblem, activatedLocalDate, selection.activatedAt(), dailyProblemCount);
    boolean planCompleted = scheduled.stream()
        .allMatch(item -> item.status() == PracticeProgressStatus.COMPLETED
            || item.status() == PracticeProgressStatus.SKIPPED);
    List<ScheduledProblem> open = scheduled.stream()
        .filter(item -> item.status() != PracticeProgressStatus.COMPLETED
            && item.status() != PracticeProgressStatus.SKIPPED)
        .toList();

    List<TodayPackSection> sections = sections(plan.id(), open, today, displayDate, packOffset);
    LocalDate nextPackDate = open.stream()
        .map(ScheduledProblem::scheduledDate)
        .filter(date -> date.isAfter(displayDate))
        .min(LocalDate::compareTo)
        .orElse(null);
    long maxCarryoverDays = open.stream()
        .filter(item -> item.scheduledDate().isBefore(today))
        .mapToLong(item -> ChronoUnit.DAYS.between(item.scheduledDate(), today))
        .max()
        .orElse(0);
    String notice = maxCarryoverDays >= CARRYOVER_NOTICE_THRESHOLD_DAYS
        ? "顺延题已积压 7 天以上，建议先完成待补题，或一键清账重新开始。"
        : null;
    TodayPackState state = state(planCompleted, sections, packOffset);

    return new TodayPack(
        state,
        today,
        zoneId.getId(),
        packOffset,
        new TodayPackActivePlan(
            plan.id(),
            plan.plan().title(),
            selection.activatedAt(),
            rhythm.dailyProblemCount(),
            rhythm.trainingDaysPerWeek(),
            rhythm.remainingProblemCount()),
        sections,
        notice,
        null,
        nextPackDate);
  }

  private TodayPack noActivePack(ZoneId zoneId, LocalDate today, int packOffset) {
    return new TodayPack(
        TodayPackState.NO_ACTIVE_PLAN,
        today,
        zoneId.getId(),
        packOffset,
        null,
        List.of(),
        null,
        new TodayPackRecommendedPlan(
            RECOMMENDED_TEMPLATE_ID,
            "NeetCode Blind 75 面试核心题",
            "一条覆盖常见面试主题的默认训练路线，可直接生成今日题包。"),
        null);
  }

  private List<ScheduledProblem> scheduledProblems(
      LearningPlan plan,
      Map<ProblemKey, PracticeProgress> progressByProblem,
      LocalDate activatedLocalDate,
      Instant activatedAt,
      int dailyProblemCount) {
    List<LearningPlanPhaseDraft> phases = plan.plan().phases().stream()
        .sorted(Comparator.comparingInt(LearningPlanPhaseDraft::phaseIndex))
        .toList();
    List<ScheduledProblem> result = new ArrayList<>();
    int scheduledIndex = 0;
    for (LearningPlanPhaseDraft phase : phases) {
      List<LearningPlanProblemDraft> problems = phase.problems().stream()
          .sorted(Comparator.comparingInt(LearningPlanProblemDraft::sortOrder))
          .toList();
      for (LearningPlanProblemDraft problem : problems) {
        PracticeProgress progress = progressByProblem.get(new ProblemKey(phase.phaseIndex(), problem.slug()));
        PracticeProgressStatus status = progress == null
            ? PracticeProgressStatus.NOT_STARTED
            : progress.status();
        // 重置题包后，重置前已完成/跳过的题不应继续占用新的每日名额。
        boolean completedBeforeActivation = terminalBeforeActivation(
            progress, status, activatedAt, PracticeProgressStatus.COMPLETED);
        boolean skippedBeforeActivation = terminalBeforeActivation(
            progress, status, activatedAt, PracticeProgressStatus.SKIPPED);
        LocalDate scheduledDate = activatedLocalDate.plusDays(scheduledIndex / dailyProblemCount);
        result.add(new ScheduledProblem(phase.phaseIndex(), problem, status, scheduledDate));
        if (!completedBeforeActivation && !skippedBeforeActivation) {
          scheduledIndex++;
        }
      }
    }
    return result;
  }

  private boolean terminalBeforeActivation(
      PracticeProgress progress,
      PracticeProgressStatus status,
      Instant activatedAt,
      PracticeProgressStatus terminalStatus) {
    if (progress == null || status != terminalStatus) {
      return false;
    }
    Instant terminalAt = terminalStatus == PracticeProgressStatus.COMPLETED
        ? progress.completedAt()
        : progress.skippedAt();
    Instant effectiveAt = terminalAt == null ? progress.updatedAt() : terminalAt;
    return effectiveAt != null && effectiveAt.isBefore(activatedAt);
  }

  private List<TodayPackSection> sections(
      long planId,
      List<ScheduledProblem> open,
      LocalDate today,
      LocalDate displayDate,
      int packOffset) {
    if (packOffset > 0) {
      List<TodayPackProblem> futureProblems = open.stream()
          .filter(item -> item.scheduledDate().isEqual(displayDate))
          .map(item -> toProblem(planId, item, today))
          .toList();
      if (futureProblems.isEmpty()) {
        return List.of();
      }
      return List.of(new TodayPackSection(TodayPackSectionType.FUTURE, "未来题包", displayDate, futureProblems));
    }

    List<TodayPackSection> sections = new ArrayList<>();
    List<TodayPackProblem> carryover = open.stream()
        .filter(item -> item.scheduledDate().isBefore(today))
        .map(item -> toProblem(planId, item, today))
        .toList();
    if (!carryover.isEmpty()) {
      sections.add(new TodayPackSection(TodayPackSectionType.CARRYOVER, "顺延 / 待补", null, carryover));
    }
    List<TodayPackProblem> todayProblems = open.stream()
        .filter(item -> item.scheduledDate().isEqual(today))
        .map(item -> toProblem(planId, item, today))
        .toList();
    if (!todayProblems.isEmpty()) {
      sections.add(new TodayPackSection(TodayPackSectionType.TODAY, "今天的", today, todayProblems));
    }
    return sections;
  }

  private TodayPackProblem toProblem(long planId, ScheduledProblem item, LocalDate today) {
    LearningPlanProblemDraft problem = item.problem();
    long carryoverDays = Math.max(0, ChronoUnit.DAYS.between(item.scheduledDate(), today));
    return new TodayPackProblem(
        planId,
        item.phaseIndex(),
        problem.slug(),
        problem.frontendId(),
        problem.title(),
        problem.titleCn(),
        problem.difficulty(),
        problem.tags(),
        item.status(),
        item.scheduledDate(),
        carryoverDays);
  }

  private TodayPackState state(boolean planCompleted, List<TodayPackSection> sections, int packOffset) {
    if (planCompleted) {
      return TodayPackState.PLAN_COMPLETED;
    }
    if (sections.isEmpty() && packOffset == 0) {
      return TodayPackState.DONE_TODAY;
    }
    return TodayPackState.READY;
  }

  private List<PracticeProgress> progressByPlan(long userId, long planId) {
    if (practiceSessionRepository == null) {
      return List.of();
    }
    try {
      return practiceSessionRepository.findProgressByPlan(userId, planId);
    } catch (UnsupportedOperationException exception) {
      return List.of();
    }
  }

  private Map<ProblemKey, PracticeProgress> progressByProblem(List<PracticeProgress> progress) {
    Map<ProblemKey, PracticeProgress> result = new HashMap<>();
    if (progress == null) {
      return result;
    }
    for (PracticeProgress item : progress) {
      result.put(new ProblemKey(item.phaseIndex(), item.problemSlug()), item);
    }
    return result;
  }

  private ZoneId zoneId(String timezone) {
    String normalized = timezone == null || timezone.isBlank() ? "UTC" : timezone.trim();
    try {
      return ZoneId.of(normalized);
    } catch (DateTimeException exception) {
      throw new LearningPlanException("TODAY_PACK_TIMEZONE_INVALID", "时区参数无效。");
    }
  }

  private record ProblemKey(int phaseIndex, String slug) {
  }

  private record ScheduledProblem(
      int phaseIndex,
      LearningPlanProblemDraft problem,
      PracticeProgressStatus status,
      LocalDate scheduledDate
  ) {
  }
}
