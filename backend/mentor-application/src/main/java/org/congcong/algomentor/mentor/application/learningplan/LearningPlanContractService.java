package org.congcong.algomentor.mentor.application.learningplan;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.congcong.algomentor.mentor.application.practice.PracticeProgress;
import org.congcong.algomentor.mentor.application.practice.PracticeProgressStatus;

public class LearningPlanContractService {

  private static final int REAL_RATE_MIN_COMPLETED_COUNT = 3;
  private static final int AUTO_PAUSE_IDLE_DAYS = 7;

  private final Clock clock;
  private final LearningPlanLoadService loadService;

  public LearningPlanContractService() {
    this(Clock.systemUTC(), new LearningPlanLoadService());
  }

  public LearningPlanContractService(Clock clock, LearningPlanLoadService loadService) {
    this.clock = clock == null ? Clock.systemUTC() : clock;
    this.loadService = loadService == null ? new LearningPlanLoadService(this.clock) : loadService;
  }

  public LearningPlanLivingContractSummary summarize(LearningPlan plan, List<PracticeProgress> progress) {
    return summarize(plan, progress, LearningPlanContractState.empty(plan.userId(), plan.id()));
  }

  public LearningPlanLivingContractSummary summarize(
      LearningPlan plan,
      List<PracticeProgress> progress,
      LearningPlanContractState state
  ) {
    Instant now = clock.instant();
    LearningPlanDraftPlan snapshot = plan.plan();
    Map<ProblemKey, LearningPlanProblemDraft> problems = planProblems(snapshot);
    Map<ProblemKey, PracticeProgress> progressByProblem = progressByProblem(progress);
    int completed = 0;
    int skipped = 0;
    List<PracticeProgress> completedProgress = new ArrayList<>();
    List<String> unresolvedSlugs = new ArrayList<>();
    for (Map.Entry<ProblemKey, LearningPlanProblemDraft> entry : problems.entrySet()) {
      PracticeProgress item = progressByProblem.get(entry.getKey());
      PracticeProgressStatus status = item == null ? PracticeProgressStatus.NOT_STARTED : item.status();
      if (status == PracticeProgressStatus.COMPLETED) {
        completed++;
        completedProgress.add(item);
      } else if (status == PracticeProgressStatus.SKIPPED) {
        skipped++;
        unresolvedSlugs.add(entry.getValue().slug());
      } else {
        unresolvedSlugs.add(entry.getValue().slug());
      }
    }
    int total = problems.size();
    int open = Math.max(0, total - completed - skipped);
    Estimation estimation = estimate(plan, completedProgress, open, state, now);
    LearningPlanVisibleStatus status = visibleStatus(plan, state, total, completed, open, completedProgress, estimation.date(), now);
    LearningPlanCompletionSummary completionSummary = completionSummary(
        plan,
        problems,
        progressByProblem,
        total,
        completed,
        skipped,
        open,
        unresolvedSlugs,
        status,
        now);
    return new LearningPlanLivingContractSummary(
        total,
        completed,
        skipped,
        open,
        total == 0 ? 0D : round1((double) completed * 100D / total),
        estimation.date(),
        estimation.source(),
        status,
        loadService.nextTrainingPackage(plan, progress),
        notice(state, status),
        completionSummary);
  }

  private Map<ProblemKey, LearningPlanProblemDraft> planProblems(LearningPlanDraftPlan plan) {
    Map<ProblemKey, LearningPlanProblemDraft> problems = new LinkedHashMap<>();
    for (LearningPlanPhaseDraft phase : plan.phases()) {
      for (LearningPlanProblemDraft problem : phase.problems()) {
        problems.putIfAbsent(new ProblemKey(phase.phaseIndex(), problem.slug()), problem);
      }
    }
    return problems;
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

  private Estimation estimate(
      LearningPlan plan,
      List<PracticeProgress> completedProgress,
      int open,
      LearningPlanContractState state,
      Instant now
  ) {
    if (open == 0) {
      return new Estimation(toDate(now), LearningPlanEstimationSource.COMPLETED);
    }
    if (state != null && state.frozenEstimatedCompletionDate() != null) {
      return new Estimation(state.frozenEstimatedCompletionDate(), LearningPlanEstimationSource.FROZEN);
    }
    if (completedProgress.size() < REAL_RATE_MIN_COMPLETED_COUNT) {
      return new Estimation(plannedEndDate(plan), LearningPlanEstimationSource.COLD_START_PLAN_QUOTA);
    }
    double dailyRate = recentDailyRate(completedProgress, now);
    if (dailyRate <= 0D) {
      dailyRate = historicalDailyRate(completedProgress);
    }
    if (dailyRate <= 0D) {
      return new Estimation(plannedEndDate(plan), LearningPlanEstimationSource.RECENT_COMPLETION_RATE);
    }
    long remainingDays = Math.max(1L, (long) Math.ceil(open / dailyRate));
    return new Estimation(toDate(now.plus(Duration.ofDays(remainingDays))), LearningPlanEstimationSource.RECENT_COMPLETION_RATE);
  }

  private double recentDailyRate(List<PracticeProgress> completedProgress, Instant now) {
    Instant since = now.minus(Duration.ofDays(7));
    long recentCompleted = completedProgress.stream()
        .map(PracticeProgress::completedAt)
        .filter(completedAt -> completedAt != null && !completedAt.isBefore(since) && !completedAt.isAfter(now))
        .count();
    return recentCompleted / 7D;
  }

  private double historicalDailyRate(List<PracticeProgress> completedProgress) {
    List<Instant> timestamps = completedProgress.stream()
        .map(PracticeProgress::completedAt)
        .filter(completedAt -> completedAt != null)
        .sorted()
        .toList();
    if (timestamps.size() < REAL_RATE_MIN_COMPLETED_COUNT) {
      return 0D;
    }
    long days = Math.max(1L, Duration.between(timestamps.get(0), timestamps.get(timestamps.size() - 1)).toDays());
    return timestamps.size() / (double) days;
  }

  private LearningPlanVisibleStatus visibleStatus(
      LearningPlan plan,
      LearningPlanContractState state,
      int total,
      int completed,
      int open,
      List<PracticeProgress> completedProgress,
      LocalDate estimatedDate,
      Instant now
  ) {
    if (state != null && state.closedOut()) {
      return LearningPlanVisibleStatus.CLOSED_OUT;
    }
    if (total > 0 && completed >= total) {
      return LearningPlanVisibleStatus.COMPLETED;
    }
    if (state != null && state.paused()) {
      return LearningPlanVisibleStatus.PAUSED;
    }
    if (open > 0 && !completedProgress.isEmpty()) {
      Instant latestCompleted = completedProgress.stream()
          .map(PracticeProgress::completedAt)
          .filter(completedAt -> completedAt != null)
          .max(Comparator.naturalOrder())
          .orElse(null);
      if (latestCompleted != null && Duration.between(latestCompleted, now).toDays() >= AUTO_PAUSE_IDLE_DAYS) {
        return LearningPlanVisibleStatus.PAUSED;
      }
    }
    if (estimatedDate != null && estimatedDate.isAfter(plannedEndDate(plan).plusDays(3))) {
      return LearningPlanVisibleStatus.NEEDS_REBALANCE;
    }
    return LearningPlanVisibleStatus.ON_TRACK;
  }

  private LearningPlanCompletionSummary completionSummary(
      LearningPlan plan,
      Map<ProblemKey, LearningPlanProblemDraft> problems,
      Map<ProblemKey, PracticeProgress> progressByProblem,
      int total,
      int completed,
      int skipped,
      int open,
      List<String> unresolvedSlugs,
      LearningPlanVisibleStatus status,
      Instant now
  ) {
    if (status != LearningPlanVisibleStatus.COMPLETED && status != LearningPlanVisibleStatus.CLOSED_OUT) {
      return null;
    }
    return new LearningPlanCompletionSummary(
        total == 0 ? 0D : round1((double) completed * 100D / total),
        Math.max(0L, Duration.between(plan.createdAt(), now).toDays()),
        completed,
        skipped,
        open,
        rankedTags(problems, progressByProblem, PracticeProgressStatus.COMPLETED),
        weakTags(problems, progressByProblem),
        unresolvedSlugs);
  }

  private List<String> rankedTags(
      Map<ProblemKey, LearningPlanProblemDraft> problems,
      Map<ProblemKey, PracticeProgress> progressByProblem,
      PracticeProgressStatus expectedStatus
  ) {
    Map<String, Long> counts = new HashMap<>();
    for (Map.Entry<ProblemKey, LearningPlanProblemDraft> entry : problems.entrySet()) {
      PracticeProgress item = progressByProblem.get(entry.getKey());
      if (item == null || item.status() != expectedStatus) {
        continue;
      }
      for (String tag : entry.getValue().tags()) {
        String normalized = normalizeTag(tag);
        if (!normalized.isBlank()) {
          counts.merge(normalized, 1L, Long::sum);
        }
      }
    }
    return counts.entrySet().stream()
        .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
        .limit(5)
        .map(Map.Entry::getKey)
        .toList();
  }

  private List<String> weakTags(
      Map<ProblemKey, LearningPlanProblemDraft> problems,
      Map<ProblemKey, PracticeProgress> progressByProblem
  ) {
    Map<String, Long> counts = new HashMap<>();
    for (Map.Entry<ProblemKey, LearningPlanProblemDraft> entry : problems.entrySet()) {
      PracticeProgress item = progressByProblem.get(entry.getKey());
      PracticeProgressStatus status = item == null ? PracticeProgressStatus.NOT_STARTED : item.status();
      if (status == PracticeProgressStatus.COMPLETED) {
        continue;
      }
      for (String tag : entry.getValue().tags()) {
        String normalized = normalizeTag(tag);
        if (!normalized.isBlank()) {
          counts.merge(normalized, 1L, Long::sum);
        }
      }
    }
    return counts.entrySet().stream()
        .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
        .limit(5)
        .map(Map.Entry::getKey)
        .toList();
  }

  private String normalizeTag(String tag) {
    return tag == null ? "" : tag.trim().toLowerCase(Locale.ROOT);
  }

  private String notice(LearningPlanContractState state, LearningPlanVisibleStatus status) {
    if (state != null && !state.paused() && state.lastRebalanceNoticeAt() != null) {
      return "未完成题已顺延到后续训练包。";
    }
    if (status == LearningPlanVisibleStatus.PAUSED) {
      return "连续 7 天没有完成记录，学习计划已进入暂停视图。";
    }
    if (status == LearningPlanVisibleStatus.NEEDS_REBALANCE) {
      return "预计完成日已顺延，建议降低本周目标或生成补充计划。";
    }
    return null;
  }

  private LocalDate plannedEndDate(LearningPlan plan) {
    return toDate(plan.createdAt().plus(Duration.ofDays(Math.max(1, plan.plan().durationWeeks()) * 7L)));
  }

  private LocalDate toDate(Instant instant) {
    return LocalDate.ofInstant(instant, ZoneOffset.UTC);
  }

  private double round1(double value) {
    return Math.round(value * 10D) / 10D;
  }

  private record ProblemKey(int phaseIndex, String problemSlug) {
  }

  private record Estimation(LocalDate date, LearningPlanEstimationSource source) {
  }
}
