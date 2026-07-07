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
      LearningPlanRhythmMode rhythmMode,
      LearningPlanCoveragePolicy coveragePolicy
  ) {
    LearningPlanCoveragePolicy effectivePolicy = coveragePolicy == null
        ? coveragePolicyFromMetadata(plan)
        : coveragePolicy;
    LearningPlanRhythmMode effectiveRhythm = rhythmMode == null
        ? rhythmModeFromMetadata(plan)
        : rhythmMode;
    LearningPlanLoadSummary summary = summarize(plan, effectivePolicy);
    List<LearningPlanWeeklyBucket> buckets = weeklyBuckets(plan, effectivePolicy);
    LearningPlanTrainingPackage trainingPackage = nextTrainingPackage(plan, buckets, effectiveRhythm);
    Map<String, Object> metadata = new LinkedHashMap<>(plan.metadata());
    metadata.put(LearningPlanDraftMetadataKeys.RHYTHM_MODE, effectiveRhythm.name());
    metadata.put(LearningPlanDraftMetadataKeys.COVERAGE_POLICY, effectivePolicy.name());
    metadata.put(LearningPlanDraftMetadataKeys.LOAD_SUMMARY, toMetadata(summary));
    metadata.put(LearningPlanDraftMetadataKeys.WEEKLY_BUCKETS, buckets.stream().map(this::toMetadata).toList());
    metadata.put(LearningPlanDraftMetadataKeys.NEXT_TRAINING_PACKAGE, toMetadata(trainingPackage));
    metadata.put(LearningPlanDraftMetadataKeys.LOAD_RISK, isOverloaded(summary) ? LOAD_RISK_OVERLOADED : LOAD_RISK_NORMAL);
    return copyWithMetadata(plan, metadata);
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

  public List<LearningPlanRhythmOption> rhythmOptions(LearningPlanTemplate template) {
    return List.of(
        rhythmOption(template, LearningPlanRhythmMode.RECOMMENDED),
        rhythmOption(template, LearningPlanRhythmMode.RELAXED),
        rhythmOption(template, LearningPlanRhythmMode.SPRINT));
  }

  public LearningPlanRhythmOption rhythmOption(LearningPlanTemplate template, LearningPlanRhythmMode mode) {
    LearningPlanRhythmMode effectiveMode = mode == null ? LearningPlanRhythmMode.RECOMMENDED : mode;
    int phaseCount = Math.max(1, template.phases().size());
    int durationWeeks = switch (effectiveMode) {
      case RECOMMENDED -> template.defaultDurationWeeks();
      case RELAXED -> (int) Math.ceil(template.defaultDurationWeeks() * 1.5D);
      case SPRINT -> Math.max(phaseCount, (int) Math.ceil(template.defaultDurationWeeks() * 0.75D));
    };
    int weeklyHours = switch (effectiveMode) {
      case RECOMMENDED, RELAXED -> template.defaultWeeklyHours();
      case SPRINT -> (int) Math.ceil(template.defaultWeeklyHours() * 1.5D);
    };
    int[] trainingDays = trainingDaysPerWeek(effectiveMode);
    LearningPlanCoveragePolicy coveragePolicy = switch (effectiveMode) {
      case RECOMMENDED -> LearningPlanCoveragePolicy.FULL_ROUTE;
      case RELAXED -> LearningPlanCoveragePolicy.FULL_ROUTE_WITH_REVIEW_BUFFER;
      case SPRINT -> LearningPlanCoveragePolicy.FULL_ROUTE_FAST;
    };
    LearningPlanLoadSummary loadSummary = summarizeTemplate(
        template,
        Math.max(phaseCount, Math.max(1, durationWeeks)),
        Math.max(1, weeklyHours),
        coveragePolicy);
    int[] dailyProblems = dailyProblemRange(
        loadSummary.plannedProblemCount(),
        Math.max(phaseCount, Math.max(1, durationWeeks)),
        trainingDays);
    return new LearningPlanRhythmOption(
        effectiveMode,
        Math.max(phaseCount, Math.max(1, durationWeeks)),
        Math.max(1, weeklyHours),
        trainingDays[0],
        trainingDays[1],
        dailyProblems[0],
        dailyProblems[1],
        coveragePolicy,
        loadSummary);
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
    return nextTrainingPackage(plan, List.of(), clock.instant());
  }

  public LearningPlanTrainingPackage nextTrainingPackage(
      LearningPlan plan,
      List<PracticeProgress> progress
  ) {
    return nextTrainingPackage(plan.plan(), progress, clock.instant());
  }

  public LearningPlanTrainingPackage nextTrainingPackage(
      LearningPlanDraftPlan plan,
      List<PracticeProgress> progress,
      Instant now
  ) {
    LearningPlanRhythmMode mode = rhythmModeFromMetadata(plan);
    List<LearningPlanWeeklyBucket> buckets = weeklyBuckets(plan);
    if (buckets.isEmpty()) {
      return nextTrainingPackage(plan, buckets, mode);
    }
    Map<String, PracticeProgressStatus> progressBySlug = progressBySlug(progress);
    LearningPlanWeeklyBucket selectedBucket = buckets.stream()
        .filter(bucket -> bucket.problemSlugs().stream()
            .anyMatch(slug -> progressBySlug.getOrDefault(slug, PracticeProgressStatus.NOT_STARTED)
                != PracticeProgressStatus.COMPLETED
                && progressBySlug.getOrDefault(slug, PracticeProgressStatus.NOT_STARTED)
                != PracticeProgressStatus.SKIPPED))
        .findFirst()
        .orElse(buckets.get(Math.min(buckets.size(), Math.max(1, currentWeek(null, now, plan.durationWeeks()))) - 1));
    List<String> remainingSlugs = selectedBucket.problemSlugs().stream()
        .filter(slug -> progressBySlug.getOrDefault(slug, PracticeProgressStatus.NOT_STARTED)
            != PracticeProgressStatus.COMPLETED
            && progressBySlug.getOrDefault(slug, PracticeProgressStatus.NOT_STARTED)
            != PracticeProgressStatus.SKIPPED)
        .toList();
    return packageForBucket(plan, selectedBucket, remainingSlugs, mode);
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

  private LearningPlanTrainingPackage nextTrainingPackage(
      LearningPlanDraftPlan plan,
      List<LearningPlanWeeklyBucket> buckets,
      LearningPlanRhythmMode mode
  ) {
    if (buckets.isEmpty()) {
      return new LearningPlanTrainingPackage(1, 0, "建立错题复盘记录。", estimateTrainingMinutes(plan, mode), List.of());
    }
    LearningPlanWeeklyBucket bucket = buckets.stream()
        .filter(item -> !item.problemSlugs().isEmpty())
        .findFirst()
        .orElse(buckets.get(0));
    return packageForBucket(plan, bucket, bucket.problemSlugs(), mode);
  }

  private LearningPlanTrainingPackage packageForBucket(
      LearningPlanDraftPlan plan,
      LearningPlanWeeklyBucket bucket,
      List<String> candidateSlugs,
      LearningPlanRhythmMode mode
  ) {
    int[] trainingDays = trainingDaysPerWeek(mode);
    int trainingDaysMax = Math.max(1, trainingDays[1]);
    int newProblemCount = bucket.plannedProblemCount() <= 0
        ? 0
        : Math.max(1, (int) Math.ceil((double) bucket.plannedProblemCount() / trainingDaysMax));
    List<String> prioritySlugs = candidateSlugs.stream()
        .limit(Math.max(1, newProblemCount))
        .toList();
    String reviewTask = bucket.reviewAdvice() == null || bucket.reviewAdvice().isBlank()
        ? "复盘本次训练中的卡点和错因。"
        : bucket.reviewAdvice();
    return new LearningPlanTrainingPackage(
        bucket.weekIndex(),
        Math.min(newProblemCount, candidateSlugs.size()),
        reviewTask,
        estimateTrainingMinutes(plan, mode),
        prioritySlugs);
  }

  private int estimateTrainingMinutes(LearningPlanDraftPlan plan, LearningPlanRhythmMode mode) {
    int[] trainingDays = trainingDaysPerWeek(mode);
    int days = Math.max(1, trainingDays[0]);
    return (int) Math.ceil(Math.max(1, plan.weeklyHours()) * 60D / days);
  }

  private int[] trainingDaysPerWeek(LearningPlanRhythmMode mode) {
    return switch (mode == null ? LearningPlanRhythmMode.RECOMMENDED : mode) {
      case RELAXED -> new int[] {4, 4};
      case SPRINT -> new int[] {6, 7};
      case RECOMMENDED -> new int[] {5, 5};
    };
  }

  private int[] dailyProblemRange(int problemCount, int durationWeeks, int[] trainingDays) {
    int minDays = Math.max(1, durationWeeks * Math.max(1, trainingDays[1]));
    int maxDays = Math.max(1, durationWeeks * Math.max(1, trainingDays[0]));
    int min = problemCount <= 0 ? 0 : Math.max(1, (int) Math.floor((double) problemCount / minDays));
    int max = problemCount <= 0 ? 0 : Math.max(min, (int) Math.ceil((double) problemCount / maxDays));
    return new int[] {min, max};
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

  private LearningPlanRhythmMode rhythmModeFromMetadata(LearningPlanDraftPlan plan) {
    if (plan != null) {
      Object value = plan.metadata().get(LearningPlanDraftMetadataKeys.RHYTHM_MODE);
      if (value instanceof String text) {
        try {
          return LearningPlanRhythmMode.valueOf(text);
        } catch (IllegalArgumentException ignored) {
          return LearningPlanRhythmMode.RECOMMENDED;
        }
      }
    }
    return LearningPlanRhythmMode.RECOMMENDED;
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

  private Map<String, Object> toMetadata(LearningPlanWeeklyBucket bucket) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("weekIndex", bucket.weekIndex());
    metadata.put("title", bucket.title());
    metadata.put("plannedProblemCount", bucket.plannedProblemCount());
    metadata.put("plannedLoadPoints", bucket.plannedLoadPoints());
    metadata.put("problemSlugs", bucket.problemSlugs());
    metadata.put("reviewAdvice", bucket.reviewAdvice());
    return metadata;
  }

  private Map<String, Object> toMetadata(LearningPlanTrainingPackage trainingPackage) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("weekIndex", trainingPackage.weekIndex());
    metadata.put("newProblemCount", trainingPackage.newProblemCount());
    metadata.put("reviewTask", trainingPackage.reviewTask());
    metadata.put("estimatedMinutes", trainingPackage.estimatedMinutes());
    metadata.put("priorityProblemSlugs", trainingPackage.priorityProblemSlugs());
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
}
