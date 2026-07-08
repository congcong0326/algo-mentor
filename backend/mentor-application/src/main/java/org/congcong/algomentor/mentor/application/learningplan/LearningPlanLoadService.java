package org.congcong.algomentor.mentor.application.learningplan;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.congcong.algomentor.mentor.application.learningplan.template.LearningPlanTemplate;
import org.congcong.algomentor.mentor.application.learningplan.template.LearningPlanTemplatePhase;
import org.congcong.algomentor.mentor.application.learningplan.template.LearningPlanTemplateProblemRef;
import org.congcong.algomentor.mentor.application.practice.PracticeProgress;
import org.congcong.algomentor.mentor.application.practice.PracticeProgressStatus;

public class LearningPlanLoadService {

  public static final String LOAD_RISK_NORMAL = "NORMAL";
  public static final String LOAD_RISK_OVERLOADED = "OVERLOADED";
  public static final int MIN_DAILY_PROBLEM_COUNT = 1;
  public static final int MAX_DAILY_PROBLEM_COUNT = 10;
  public static final int MIN_TRAINING_DAYS_PER_WEEK = 1;
  public static final int MAX_TRAINING_DAYS_PER_WEEK = 7;

  private static final double CAPACITY_POINTS_PER_HOUR = 1.0D;
  private static final double SPECIAL_TOPIC_BONUS = 0.5D;
  private static final double REVIEW_BUFFER_PER_PROBLEM = 0.5D;
  private static final double RELAXED_RATIO_LIMIT = 0.85D;
  private static final double RECOMMENDED_RATIO_LIMIT = 1.10D;
  private static final double TIGHT_RATIO_LIMIT = 1.30D;
  private static final Set<String> SPECIAL_TAGS = Set.of("GRAPH", "DYNAMIC PROGRAMMING", "DP");

  private final Clock clock;

  public LearningPlanLoadService() {
    this(Clock.systemUTC());
  }

  public LearningPlanLoadService(Clock clock) {
    this.clock = clock == null ? Clock.systemUTC() : clock;
  }

  public double weeklyCapacityPoints(Integer weeklyHours) {
    return round1(Math.max(0, weeklyHours == null ? 0 : weeklyHours) * CAPACITY_POINTS_PER_HOUR);
  }

  public LearningPlanDraftPlan withLoadMetadata(
      LearningPlanDraftPlan plan,
      LearningPlanCoveragePolicy coveragePolicy
  ) {
    return withRhythmMetadata(plan, defaultDailyProblemCount(plan), defaultTrainingDaysPerWeek(), coveragePolicy);
  }

  public LearningPlanDraftPlan withRhythmMetadata(
      LearningPlanDraftPlan plan,
      Integer dailyProblemCount,
      Integer trainingDaysPerWeek
  ) {
    return withRhythmMetadata(plan, dailyProblemCount, trainingDaysPerWeek, null);
  }

  public LearningPlanDraftPlan withRhythmMetadata(
      LearningPlanDraftPlan plan,
      Integer dailyProblemCount,
      Integer trainingDaysPerWeek,
      LearningPlanCoveragePolicy coveragePolicy
  ) {
    int effectiveDailyProblemCount = validatedDailyProblemCount(dailyProblemCount);
    int effectiveTrainingDaysPerWeek = validatedTrainingDaysPerWeek(trainingDaysPerWeek);
    LearningPlanCoveragePolicy effectivePolicy = coveragePolicy == null ? coveragePolicyFromMetadata(plan) : coveragePolicy;
    LearningPlanLoadSummary summary = summarize(plan, effectivePolicy);
    Map<String, Object> metadata = new LinkedHashMap<>(plan.metadata());
    metadata.put(LearningPlanDraftMetadataKeys.DAILY_PROBLEM_COUNT, effectiveDailyProblemCount);
    metadata.put(LearningPlanDraftMetadataKeys.TRAINING_DAYS_PER_WEEK, effectiveTrainingDaysPerWeek);
    metadata.put(LearningPlanDraftMetadataKeys.COVERAGE_POLICY, effectivePolicy.name());
    metadata.put(LearningPlanDraftMetadataKeys.LOAD_SUMMARY, toMetadata(summary));
    metadata.put(LearningPlanDraftMetadataKeys.LOAD_RISK, isOverloaded(summary) ? LOAD_RISK_OVERLOADED : LOAD_RISK_NORMAL);
    return copyWithMetadata(plan, metadata);
  }

  public LearningPlanRhythmSettings defaultRhythmSettings(LearningPlanTemplate template) {
    int totalProblemCount = template == null ? 0 : Math.max(0, template.matchedProblemCount());
    int durationWeeks = template == null ? 1 : Math.max(1, template.defaultDurationWeeks());
    int dailyProblemCount = clampDailyProblemCount((int) Math.ceil(totalProblemCount / Math.max(1D, durationWeeks * 5D)));
    return rhythmSettings(dailyProblemCount, defaultTrainingDaysPerWeek(), totalProblemCount, 0, 0);
  }

  public LearningPlanLoadSummary defaultLoadSummary(LearningPlanTemplate template) {
    return summarizeTemplate(
        template,
        Math.max(1, template.defaultDurationWeeks()),
        Math.max(1, template.defaultWeeklyHours()),
        LearningPlanCoveragePolicy.FULL_ROUTE);
  }

  public LearningPlanRhythmSettings rhythmSettings(LearningPlanDraftPlan plan, List<PracticeProgress> progress) {
    Map<ProblemKey, PracticeProgressStatus> progressByProblem = progressStatusByProblem(progress);
    int total = 0;
    int completed = 0;
    int skipped = 0;
    for (LearningPlanPhaseDraft phase : plan.phases()) {
      for (LearningPlanProblemDraft problem : phase.problems()) {
        total++;
        PracticeProgressStatus status = progressByProblem.getOrDefault(
            new ProblemKey(phase.phaseIndex(), problem.slug()),
            PracticeProgressStatus.NOT_STARTED);
        if (status == PracticeProgressStatus.COMPLETED) {
          completed++;
        } else if (status == PracticeProgressStatus.SKIPPED) {
          skipped++;
        }
      }
    }
    return rhythmSettings(
        dailyProblemCountFromMetadata(plan),
        trainingDaysPerWeekFromMetadata(plan),
        total,
        completed,
        skipped);
  }

  public void validateRhythm(Integer dailyProblemCount, Integer trainingDaysPerWeek) {
    validatedDailyProblemCount(dailyProblemCount);
    validatedTrainingDaysPerWeek(trainingDaysPerWeek);
  }

  public LearningPlanLoadSummary summarize(LearningPlanDraftPlan plan) {
    return summarize(plan, coveragePolicyFromMetadata(plan));
  }

  public LearningPlanLoadSummary summarize(LearningPlanDraftPlan plan, LearningPlanCoveragePolicy coveragePolicy) {
    boolean reviewBufferIncluded = reviewBufferIncluded(coveragePolicy);
    int problemCount = 0;
    double loadPoints = 0D;
    for (LearningPlanPhaseDraft phase : plan.phases()) {
      for (LearningPlanProblemDraft problem : phase.problems()) {
        problemCount++;
        loadPoints += estimateProblemLoad(problem.difficulty(), problem.tags(), reviewBufferIncluded);
      }
    }
    return summary(
        plan.durationWeeks(),
        plan.weeklyHours(),
        problemCount,
        loadPoints,
        reviewBufferIncluded);
  }

  public LearningPlanLoadSummary summarizeTemplate(
      LearningPlanTemplate template,
      int durationWeeks,
      int weeklyHours,
      LearningPlanCoveragePolicy coveragePolicy
  ) {
    boolean reviewBufferIncluded = reviewBufferIncluded(coveragePolicy);
    int problemCount = 0;
    double loadPoints = 0D;
    for (LearningPlanTemplatePhase phase : template.phases()) {
      for (LearningPlanTemplateProblemRef ref : phase.problemRefs()) {
        if (!ref.matchedProblem()) {
          continue;
        }
        problemCount++;
        loadPoints += estimateProblemLoad(ref.sourceDifficulty(), List.of(ref.pattern()), reviewBufferIncluded);
      }
    }
    return summary(durationWeeks, weeklyHours, problemCount, loadPoints, reviewBufferIncluded);
  }

  public List<LearningPlanWeeklyBucket> weeklyBuckets(LearningPlanDraftPlan plan) {
    return weeklyBuckets(plan, coveragePolicyFromMetadata(plan));
  }

  public List<LearningPlanWeeklyBucket> weeklyBuckets(
      LearningPlanDraftPlan plan,
      LearningPlanCoveragePolicy coveragePolicy
  ) {
    boolean reviewBufferIncluded = reviewBufferIncluded(coveragePolicy);
    List<LearningPlanWeeklyBucket> buckets = new ArrayList<>();
    int weekIndex = 1;
    for (LearningPlanPhaseDraft phase : plan.phases()) {
      int phaseWeeks = Math.max(1, phase.durationWeeks());
      int problemCount = phase.problems().size();
      int cursor = 0;
      for (int index = 0; index < phaseWeeks; index++) {
        int count = problemCount / phaseWeeks + (index < problemCount % phaseWeeks ? 1 : 0);
        List<LearningPlanProblemDraft> plannedProblems = phase.problems().subList(cursor, cursor + count);
        cursor += count;
        buckets.add(new LearningPlanWeeklyBucket(
            weekIndex++,
            phase.title(),
            plannedProblems.size(),
            round1(plannedProblems.stream()
                .mapToDouble(problem -> estimateProblemLoad(problem.difficulty(), problem.tags(), reviewBufferIncluded))
                .sum()),
            plannedProblems.stream().map(LearningPlanProblemDraft::slug).toList(),
            phase.reviewAdvice()));
      }
    }
    return buckets;
  }

  public LearningPlanPaceSummary paceSummary(LearningPlan plan, List<PracticeProgress> progress) {
    return paceSummary(plan, progress, clock.instant());
  }

  public LearningPlanTrainingPackage nextTrainingPackage(LearningPlanDraftPlan plan) {
    return nextTrainingPackage(plan, List.of());
  }

  public LearningPlanTrainingPackage nextTrainingPackage(
      LearningPlan plan,
      List<PracticeProgress> progress
  ) {
    return nextTrainingPackage(plan.plan(), progress);
  }

  public LearningPlanTrainingPackage nextTrainingPackage(
      LearningPlanDraftPlan plan,
      List<PracticeProgress> progress
  ) {
    Map<ProblemKey, PracticeProgressStatus> progressByProblem = progressStatusByProblem(progress);
    int dailyProblemCount = dailyProblemCountFromMetadata(plan);
    List<String> prioritySlugs = new ArrayList<>();
    String reviewTask = "复盘本次训练中的卡点和错因。";
    int weekIndex = 1;
    for (LearningPlanPhaseDraft phase : plan.phases()) {
      if (phase.reviewAdvice() != null && !phase.reviewAdvice().isBlank()) {
        reviewTask = phase.reviewAdvice();
      }
      for (LearningPlanProblemDraft problem : phase.problems()) {
        PracticeProgressStatus status = progressByProblem.getOrDefault(
            new ProblemKey(phase.phaseIndex(), problem.slug()),
            PracticeProgressStatus.NOT_STARTED);
        if (status == PracticeProgressStatus.COMPLETED || status == PracticeProgressStatus.SKIPPED) {
          continue;
        }
        prioritySlugs.add(problem.slug());
        if (prioritySlugs.size() >= dailyProblemCount) {
          return new LearningPlanTrainingPackage(
              weekIndex,
              prioritySlugs.size(),
              reviewTask,
              estimateTrainingMinutes(plan),
              prioritySlugs);
        }
      }
      weekIndex += Math.max(1, phase.durationWeeks());
    }
    if (!prioritySlugs.isEmpty()) {
      return new LearningPlanTrainingPackage(
          weekIndex,
          prioritySlugs.size(),
          reviewTask,
          estimateTrainingMinutes(plan),
          prioritySlugs);
    }
    return new LearningPlanTrainingPackage(weekIndex, 0, reviewTask, estimateTrainingMinutes(plan), List.of());
  }

  public LearningPlanPaceSummary paceSummary(
      LearningPlan plan,
      List<PracticeProgress> progress,
      Instant now
  ) {
    LearningPlanDraftPlan snapshot = plan.plan();
    LearningPlanCoveragePolicy policy = coveragePolicyFromMetadata(snapshot);
    List<LearningPlanWeeklyBucket> buckets = weeklyBuckets(snapshot, policy);
    if (buckets.isEmpty()) {
      return new LearningPlanPaceSummary(
          1,
          Math.max(1, snapshot.durationWeeks()),
          null,
          0,
          0,
          0,
          0,
          0D,
          0D,
          0D,
          LearningPlanPaceStatus.ON_TRACK,
          "按计划开始第一周训练。");
    }
    int currentWeek = currentWeek(plan.createdAt(), now, snapshot.durationWeeks());
    LearningPlanWeeklyBucket currentBucket = buckets.get(Math.min(currentWeek, buckets.size()) - 1);
    Map<String, PracticeProgressStatus> progressBySlug = progressBySlug(progress);
    boolean reviewBufferIncluded = reviewBufferIncluded(policy);
    double plannedLoadToDate = 0D;
    int plannedProblemsToDate = 0;
    int completedProblemsToDate = 0;
    int currentWeekCompleted = 0;
    int skippedProblems = 0;
    for (LearningPlanWeeklyBucket bucket : buckets) {
      if (bucket.weekIndex() <= currentWeek) {
        plannedLoadToDate += bucket.plannedLoadPoints();
        plannedProblemsToDate += bucket.plannedProblemCount();
      }
      for (String slug : bucket.problemSlugs()) {
        PracticeProgressStatus status = progressBySlug.getOrDefault(slug, PracticeProgressStatus.NOT_STARTED);
        if (status == PracticeProgressStatus.SKIPPED) {
          skippedProblems++;
        }
        if (status != PracticeProgressStatus.COMPLETED) {
          continue;
        }
        if (bucket.weekIndex() <= currentWeek) {
          completedProblemsToDate++;
        }
        if (bucket.weekIndex() == currentWeek) {
          currentWeekCompleted++;
        }
      }
    }
    double completedLoad = completedLoad(snapshot, progressBySlug, reviewBufferIncluded);
    double loadGap = round1(completedLoad - plannedLoadToDate);
    LearningPlanPaceStatus status = paceStatus(loadGap, weeklyCapacityPoints(snapshot.weeklyHours()));
    return new LearningPlanPaceSummary(
        currentWeek,
        Math.max(1, snapshot.durationWeeks()),
        currentBucket,
        currentWeekCompleted,
        plannedProblemsToDate,
        completedProblemsToDate,
        skippedProblems,
        round1(plannedLoadToDate),
        round1(completedLoad),
        loadGap,
        status,
        recommendation(status, currentBucket));
  }

  private LearningPlanLoadSummary summary(
      int durationWeeks,
      int weeklyHours,
      int problemCount,
      double loadPoints,
      boolean reviewBufferIncluded
  ) {
    double weeklyCapacity = weeklyCapacityPoints(weeklyHours);
    double totalCapacity = round1(Math.max(0, durationWeeks) * weeklyCapacity);
    double roundedLoad = round1(loadPoints);
    double ratio = totalCapacity <= 0D ? 0D : round2(roundedLoad / totalCapacity);
    String intensity = intensity(ratio);
    return new LearningPlanLoadSummary(
        durationWeeks,
        weeklyHours,
        weeklyCapacity,
        totalCapacity,
        roundedLoad,
        ratio,
        problemCount,
        durationWeeks <= 0 ? 0D : round1((double) problemCount / durationWeeks),
        intensity,
        reviewBufferIncluded,
        suggestions(intensity));
  }

  private LearningPlanDraftPlan copyWithMetadata(LearningPlanDraftPlan plan, Map<String, Object> metadata) {
    return new LearningPlanDraftPlan(
        plan.title(),
        plan.summary(),
        plan.intent(),
        plan.goal(),
        plan.durationWeeks(),
        plan.level(),
        plan.weeklyHours(),
        plan.programmingLanguage(),
        plan.difficultyPreference(),
        plan.interviewOriented(),
        plan.topicPreferences(),
        plan.profileSummary(),
        plan.phases(),
        metadata);
  }

  private double estimateProblemLoad(String difficulty, List<String> tags, boolean reviewBufferIncluded) {
    double load = switch (normalize(difficulty)) {
      case "EASY" -> 1D;
      case "HARD" -> 3D;
      default -> 2D;
    };
    if ("HARD".equals(normalize(difficulty)) || containsSpecialTopic(tags)) {
      load += SPECIAL_TOPIC_BONUS;
    }
    if (reviewBufferIncluded) {
      load += REVIEW_BUFFER_PER_PROBLEM;
    }
    return load;
  }

  private boolean containsSpecialTopic(List<String> tags) {
    if (tags == null) {
      return false;
    }
    return tags.stream()
        .filter(tag -> tag != null && !tag.isBlank())
        .map(this::normalize)
        .anyMatch(tag -> SPECIAL_TAGS.stream().anyMatch(tag::contains));
  }

  private double completedLoad(
      LearningPlanDraftPlan plan,
      Map<String, PracticeProgressStatus> progressBySlug,
      boolean reviewBufferIncluded
  ) {
    double completed = 0D;
    for (LearningPlanPhaseDraft phase : plan.phases()) {
      for (LearningPlanProblemDraft problem : phase.problems()) {
        if (progressBySlug.getOrDefault(problem.slug(), PracticeProgressStatus.NOT_STARTED)
            == PracticeProgressStatus.COMPLETED) {
          completed += estimateProblemLoad(problem.difficulty(), problem.tags(), reviewBufferIncluded);
        }
      }
    }
    return round1(completed);
  }

  private Map<String, PracticeProgressStatus> progressBySlug(List<PracticeProgress> progress) {
    Map<String, PracticeProgressStatus> result = new HashMap<>();
    if (progress == null) {
      return result;
    }
    for (PracticeProgress item : progress) {
      result.put(item.problemSlug(), item.status());
    }
    return result;
  }

  private int currentWeek(Instant createdAt, Instant now, int durationWeeks) {
    if (createdAt == null || now == null || now.isBefore(createdAt)) {
      return 1;
    }
    long days = Duration.between(createdAt, now).toDays();
    int week = (int) (days / 7L) + 1;
    return Math.max(1, Math.min(Math.max(1, durationWeeks), week));
  }

  private LearningPlanPaceStatus paceStatus(double loadGap, double weeklyCapacity) {
    double capacity = weeklyCapacity <= 0D ? 1D : weeklyCapacity;
    if (loadGap >= capacity * 0.5D) {
      return LearningPlanPaceStatus.AHEAD;
    }
    if (loadGap >= capacity * -0.5D) {
      return LearningPlanPaceStatus.ON_TRACK;
    }
    if (loadGap >= -capacity) {
      return LearningPlanPaceStatus.AT_RISK;
    }
    return LearningPlanPaceStatus.BEHIND;
  }

  private String recommendation(LearningPlanPaceStatus status, LearningPlanWeeklyBucket currentBucket) {
    String title = currentBucket == null ? "本周目标" : currentBucket.title();
    return switch (status) {
      case AHEAD -> "当前进度超前，可以追加同主题强化或安排复盘。";
      case ON_TRACK -> "当前节奏正常，继续完成「" + title + "」的本周目标。";
      case AT_RISK -> "当前节奏有风险，建议优先完成「" + title + "」剩余题目。";
      case BEHIND -> "当前明显落后，建议先压缩新题范围并增加复盘时间。";
    };
  }

  private LearningPlanRhythmSettings rhythmSettings(
      int dailyProblemCount,
      int trainingDaysPerWeek,
      int totalProblemCount,
      int completedProblemCount,
      int skippedProblemCount
  ) {
    int remainingProblemCount = Math.max(0, totalProblemCount - completedProblemCount - skippedProblemCount);
    int weeklyCapacity = Math.max(1, dailyProblemCount * trainingDaysPerWeek);
    int estimatedRemainingWeeks = remainingProblemCount == 0
        ? 0
        : (int) Math.ceil(remainingProblemCount / (double) weeklyCapacity);
    return new LearningPlanRhythmSettings(
        dailyProblemCount,
        trainingDaysPerWeek,
        totalProblemCount,
        completedProblemCount,
        skippedProblemCount,
        remainingProblemCount,
        estimatedRemainingWeeks);
  }

  private int dailyProblemCountFromMetadata(LearningPlanDraftPlan plan) {
    Object value = plan.metadata().get(LearningPlanDraftMetadataKeys.DAILY_PROBLEM_COUNT);
    if (value != null) {
      return validatedDailyProblemCount(value);
    }
    return defaultDailyProblemCount(plan);
  }

  private int trainingDaysPerWeekFromMetadata(LearningPlanDraftPlan plan) {
    Object value = plan.metadata().get(LearningPlanDraftMetadataKeys.TRAINING_DAYS_PER_WEEK);
    if (value != null) {
      return validatedTrainingDaysPerWeek(value);
    }
    return defaultTrainingDaysPerWeek();
  }

  private int defaultDailyProblemCount(LearningPlanDraftPlan plan) {
    int totalProblemCount = plan == null ? 0 : plan.phases().stream()
        .mapToInt(phase -> phase.problems().size())
        .sum();
    int durationWeeks = plan == null ? 1 : Math.max(1, plan.durationWeeks());
    return clampDailyProblemCount((int) Math.ceil(totalProblemCount / Math.max(1D, durationWeeks * 5D)));
  }

  private int defaultTrainingDaysPerWeek() {
    return 5;
  }

  private int validatedDailyProblemCount(Object value) {
    int count = intValue(value, "每天题目数不能为空。");
    if (count < MIN_DAILY_PROBLEM_COUNT || count > MAX_DAILY_PROBLEM_COUNT) {
      throw new LearningPlanException(
          "LEARNING_PLAN_RHYTHM_INVALID",
          "每天题目数必须在 1-10 之间。");
    }
    return count;
  }

  private int validatedTrainingDaysPerWeek(Object value) {
    int count = intValue(value, "每周训练天数不能为空。");
    if (count < MIN_TRAINING_DAYS_PER_WEEK || count > MAX_TRAINING_DAYS_PER_WEEK) {
      throw new LearningPlanException(
          "LEARNING_PLAN_RHYTHM_INVALID",
          "每周训练天数必须在 1-7 之间。");
    }
    return count;
  }

  private int intValue(Object value, String nullMessage) {
    if (value == null) {
      throw new LearningPlanException("LEARNING_PLAN_RHYTHM_INVALID", nullMessage);
    }
    if (value instanceof Number number) {
      return number.intValue();
    }
    if (value instanceof String text) {
      try {
        return Integer.parseInt(text);
      } catch (NumberFormatException exception) {
        throw new LearningPlanException("LEARNING_PLAN_RHYTHM_INVALID", "训练节奏参数必须是整数。");
      }
    }
    throw new LearningPlanException("LEARNING_PLAN_RHYTHM_INVALID", "训练节奏参数必须是整数。");
  }

  private int clampDailyProblemCount(int value) {
    return Math.max(MIN_DAILY_PROBLEM_COUNT, Math.min(MAX_DAILY_PROBLEM_COUNT, value));
  }

  private Map<ProblemKey, PracticeProgressStatus> progressStatusByProblem(List<PracticeProgress> progress) {
    Map<ProblemKey, PracticeProgressStatus> result = new HashMap<>();
    if (progress == null) {
      return result;
    }
    for (PracticeProgress item : progress) {
      result.put(new ProblemKey(item.phaseIndex(), item.problemSlug()), item.status());
    }
    return result;
  }

  private int estimateTrainingMinutes(LearningPlanDraftPlan plan) {
    int days = trainingDaysPerWeekFromMetadata(plan);
    return (int) Math.ceil(Math.max(1, plan.weeklyHours()) * 60D / days);
  }

  private boolean reviewBufferIncluded(LearningPlanCoveragePolicy coveragePolicy) {
    return coveragePolicy == LearningPlanCoveragePolicy.FULL_ROUTE_WITH_REVIEW_BUFFER
        || coveragePolicy == LearningPlanCoveragePolicy.FIT_USER_BUDGET;
  }

  private LearningPlanCoveragePolicy coveragePolicyFromMetadata(LearningPlanDraftPlan plan) {
    if (plan != null) {
      Object value = plan.metadata().get(LearningPlanDraftMetadataKeys.COVERAGE_POLICY);
      if (value instanceof String text) {
        try {
          return LearningPlanCoveragePolicy.valueOf(text);
        } catch (IllegalArgumentException ignored) {
          return LearningPlanCoveragePolicy.FIT_USER_BUDGET;
        }
      }
    }
    return LearningPlanCoveragePolicy.FIT_USER_BUDGET;
  }

  private String intensity(double ratio) {
    if (ratio <= RELAXED_RATIO_LIMIT) {
      return "RELAXED";
    }
    if (ratio <= RECOMMENDED_RATIO_LIMIT) {
      return "RECOMMENDED";
    }
    if (ratio <= TIGHT_RATIO_LIMIT) {
      return "TIGHT";
    }
    return "OVERLOADED";
  }

  private boolean isOverloaded(LearningPlanLoadSummary summary) {
    return "OVERLOADED".equals(summary.intensity());
  }

  private List<String> suggestions(String intensity) {
    return switch (intensity) {
      case "RELAXED" -> List.of("当前节奏有复盘缓冲，可以稳定推进。");
      case "RECOMMENDED" -> List.of("当前节奏合理，建议按周完成题目并保留复盘。");
      case "TIGHT" -> List.of("当前节奏偏紧，需要稳定投入每周时间。");
      default -> List.of("当前节奏过载，建议延长周期、增加每周投入或减少题量。");
    };
  }

  private Map<String, Object> toMetadata(LearningPlanLoadSummary summary) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("durationWeeks", summary.durationWeeks());
    metadata.put("weeklyHours", summary.weeklyHours());
    metadata.put("weeklyCapacityPoints", summary.weeklyCapacityPoints());
    metadata.put("totalCapacityPoints", summary.totalCapacityPoints());
    metadata.put("plannedLoadPoints", summary.plannedLoadPoints());
    metadata.put("loadRatio", summary.loadRatio());
    metadata.put("plannedProblemCount", summary.plannedProblemCount());
    metadata.put("averageProblemsPerWeek", summary.averageProblemsPerWeek());
    metadata.put("intensity", summary.intensity());
    metadata.put("reviewBufferIncluded", summary.reviewBufferIncluded());
    metadata.put("suggestions", summary.suggestions());
    return metadata;
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }

  private double round1(double value) {
    return Math.round(value * 10D) / 10D;
  }

  private double round2(double value) {
    return Math.round(value * 100D) / 100D;
  }

  private record ProblemKey(int phaseIndex, String slug) {
  }
}
