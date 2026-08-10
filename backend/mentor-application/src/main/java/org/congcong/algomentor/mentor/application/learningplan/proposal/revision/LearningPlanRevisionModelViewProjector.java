package org.congcong.algomentor.mentor.application.learningplan.proposal.revision;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanBrief;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanContentLocale;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftMetadataKeys;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanPhaseDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemDraft;

/** 把 canonical 修订快照投影成模型可读的精简语义视图。 */
public final class LearningPlanRevisionModelViewProjector {

  public static final int DEFAULT_MAX_INLINE_PROBLEMS = 30;
  public static final int DEFAULT_MAX_INLINE_TOKEN_ESTIMATE = 8_000;

  private final ObjectMapper objectMapper;
  private final int maxInlineProblems;
  private final int maxInlineTokenEstimate;

  public LearningPlanRevisionModelViewProjector(ObjectMapper objectMapper) {
    this(objectMapper, DEFAULT_MAX_INLINE_PROBLEMS, DEFAULT_MAX_INLINE_TOKEN_ESTIMATE);
  }

  public LearningPlanRevisionModelViewProjector(
      ObjectMapper objectMapper,
      int maxInlineProblems,
      int maxInlineTokenEstimate
  ) {
    this.objectMapper = Objects.requireNonNull(objectMapper, "Object mapper must not be null");
    this.maxInlineProblems = Math.max(1, maxInlineProblems);
    this.maxInlineTokenEstimate = Math.max(1, maxInlineTokenEstimate);
  }

  public Map<String, Object> project(LearningPlanRevisionBaseSnapshot snapshot) {
    Objects.requireNonNull(snapshot, "Learning plan revision snapshot must not be null");
    Map<String, Object> full = view(snapshot, true);
    int problemCount = problemCount(snapshot.basePlan());
    boolean inline = problemCount <= maxInlineProblems && tokenEstimate(full) <= maxInlineTokenEstimate;
    return inline ? full : view(snapshot, false);
  }

  private Map<String, Object> view(LearningPlanRevisionBaseSnapshot snapshot, boolean includeProblems) {
    LearningPlanBrief brief = snapshot.baseBrief();
    LearningPlanDraftPlan plan = snapshot.basePlan();
    Map<String, Object> result = new LinkedHashMap<>();
    result.put(
        "projectionMode",
        includeProblems
            ? LearningPlanRevisionToolContracts.PROJECTION_INLINE_FULL
            : LearningPlanRevisionToolContracts.PROJECTION_SUMMARY_WITH_TOOLS);
    result.put("constraints", constraints(brief));
    result.put("plan", plan(plan, brief.contentLocale(), includeProblems));
    result.put("signals", signals(plan));
    return immutableMap(result);
  }

  private Map<String, Object> constraints(LearningPlanBrief brief) {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("intent", brief.intent());
    values.put("objective", brief.objective());
    values.put("durationWeeks", brief.durationWeeks());
    values.put("level", brief.level());
    values.put("weeklyHours", brief.weeklyHours());
    values.put("programmingLanguage", brief.programmingLanguage());
    values.put("difficultyDistribution", brief.difficultyDistribution());
    values.put("topicPreferences", brief.topicPreferences());
    values.put("additionalConstraints", brief.additionalConstraints());
    return values;
  }

  private Map<String, Object> plan(
      LearningPlanDraftPlan plan,
      LearningPlanContentLocale locale,
      boolean includeProblems
  ) {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("title", plan.title());
    values.put("summary", plan.summary());
    values.put("problemCount", problemCount(plan));
    values.put("difficultyCounts", difficultyCounts(plan.phases().stream()
        .flatMap(phase -> phase.problems().stream()).toList()));
    List<Map<String, Object>> phases = new ArrayList<>();
    for (int index = 0; index < plan.phases().size(); index++) {
      LearningPlanPhaseDraft phase = plan.phases().get(index);
      Map<String, Object> phaseView = new LinkedHashMap<>();
      phaseView.put("ref", phaseRef(index));
      phaseView.put("title", phase.title());
      phaseView.put("focus", phase.focus());
      phaseView.put("problemCount", phase.problems().size());
      phaseView.put("difficultyCounts", difficultyCounts(phase.problems()));
      if (includeProblems) {
        phaseView.put("problems", problems(phase.problems(), locale));
      }
      phases.add(immutableMap(phaseView));
    }
    values.put("phases", List.copyOf(phases));
    return values;
  }

  private List<Map<String, Object>> problems(
      List<LearningPlanProblemDraft> problems,
      LearningPlanContentLocale locale
  ) {
    List<Map<String, Object>> result = new ArrayList<>();
    for (LearningPlanProblemDraft problem : problems) {
      Map<String, Object> values = new LinkedHashMap<>();
      values.put("slug", problem.slug());
      values.put("displayTitle", displayTitle(problem, locale));
      values.put("difficulty", problem.difficulty());
      values.put("reason", problem.reason());
      result.add(immutableMap(values));
    }
    return List.copyOf(result);
  }

  private Map<String, Integer> difficultyCounts(List<LearningPlanProblemDraft> problems) {
    int easy = 0;
    int medium = 0;
    int hard = 0;
    for (LearningPlanProblemDraft problem : problems) {
      switch (normalizeDifficulty(problem.difficulty())) {
        case "EASY" -> easy++;
        case "HARD" -> hard++;
        default -> medium++;
      }
    }
    Map<String, Integer> counts = new LinkedHashMap<>();
    counts.put("easy", easy);
    counts.put("medium", medium);
    counts.put("hard", hard);
    return Map.copyOf(counts);
  }

  private Map<String, Object> signals(LearningPlanDraftPlan plan) {
    Object loadSummary = plan.metadata().get(LearningPlanDraftMetadataKeys.LOAD_SUMMARY);
    Object intensity = loadSummary instanceof Map<?, ?> values ? values.get("intensity") : null;
    return Map.of("loadIntensity", intensity == null ? "UNKNOWN" : intensity.toString());
  }

  private String displayTitle(LearningPlanProblemDraft problem, LearningPlanContentLocale locale) {
    String preferred = locale == LearningPlanContentLocale.EN_US ? problem.title() : problem.titleCn();
    if (preferred != null && !preferred.isBlank()) {
      return preferred;
    }
    String fallback = locale == LearningPlanContentLocale.EN_US ? problem.titleCn() : problem.title();
    return fallback == null || fallback.isBlank() ? problem.slug() : fallback;
  }

  private int problemCount(LearningPlanDraftPlan plan) {
    return plan.phases().stream().mapToInt(phase -> phase.problems().size()).sum();
  }

  private int tokenEstimate(Map<String, Object> view) {
    try {
      return Math.max(1, objectMapper.writeValueAsString(view).length() / 4);
    } catch (JsonProcessingException exception) {
      throw new LearningPlanException(
          "LEARNING_PLAN_REVISION_VIEW_INVALID",
          "学习计划修订模型视图无法序列化。");
    }
  }

  private <K, V> Map<K, V> immutableMap(Map<K, V> source) {
    return Collections.unmodifiableMap(new LinkedHashMap<>(source));
  }

  public static String phaseRef(int zeroBasedIndex) {
    return "phase-" + (zeroBasedIndex + 1);
  }

  public static String normalizeDifficulty(String difficulty) {
    return difficulty == null || difficulty.isBlank()
        ? "MEDIUM"
        : difficulty.trim().toUpperCase(Locale.ROOT);
  }
}
