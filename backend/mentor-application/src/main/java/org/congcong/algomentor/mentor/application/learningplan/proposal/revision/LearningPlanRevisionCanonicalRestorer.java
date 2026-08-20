package org.congcong.algomentor.mentor.application.learningplan.proposal.revision;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanBrief;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanContentLocale;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanCoveragePolicy;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDifficultyDistribution;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftMetadataKeys;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftValidator;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanIntent;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLevel;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLoadService;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanPhaseDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemCandidate;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemCatalog;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemSearch;

/** 以冻结快照为底稿应用语义 Patch，并恢复完整 canonical 草案。 */
public final class LearningPlanRevisionCanonicalRestorer {

  private static final int MAX_CANDIDATES = 100;

  private final LearningPlanProblemCatalog problemCatalog;
  private final LearningPlanLoadService loadService;
  private final LearningPlanDraftValidator validator;

  public LearningPlanRevisionCanonicalRestorer(
      LearningPlanProblemCatalog problemCatalog,
      LearningPlanLoadService loadService,
      LearningPlanDraftValidator validator
  ) {
    this.problemCatalog = Objects.requireNonNull(problemCatalog, "Problem catalog must not be null");
    this.loadService = Objects.requireNonNull(loadService, "Learning plan load service must not be null");
    this.validator = Objects.requireNonNull(validator, "Learning plan validator must not be null");
  }

  public Compilation compile(LearningPlanRevisionBaseSnapshot snapshot, JsonNode arguments) {
    Objects.requireNonNull(snapshot, "Learning plan revision snapshot must not be null");
    if (arguments == null || !arguments.isObject()) {
      throw compilationError("INVALID_PATCH", "patch", "修订 Patch 必须是 JSON object。");
    }
    LearningPlanBrief resolvedBrief = mergeBrief(snapshot.baseBrief(), arguments.path("briefPatch"));
    List<MutablePhase> phases = mutablePhases(snapshot.basePlan());
    List<ReplacementResolution> replacements = new ArrayList<>();
    ChangeTracker tracker = new ChangeTracker();
    tracker.briefChanged = !resolvedBrief.equals(snapshot.baseBrief());
    JsonNode planPatch = arguments.path("planPatch");
    String title = patchedText(planPatch, "title", snapshot.basePlan().title());
    String summary = patchedText(planPatch, "summary", snapshot.basePlan().summary());
    tracker.planTextChanged = !Objects.equals(title, snapshot.basePlan().title())
        || !Objects.equals(summary, snapshot.basePlan().summary());
    applyPhaseChanges(phases, planPatch.path("phaseChanges"), resolvedBrief, replacements, tracker);
    if (phases.isEmpty()) {
      throw compilationError("NO_PHASES", "planPatch.phaseChanges", "修订后至少需要一个阶段。");
    }
    if (resolvedBrief.durationWeeks() < phases.size()) {
      throw compilationError(
          "PHASE_DURATION_INCOMPATIBLE",
          "briefPatch.durationWeeks",
          "总周期不能小于修订后的阶段数。");
    }

    List<LearningPlanPhaseDraft> restoredPhases = hydrateAndRebuild(
        phases,
        snapshot,
        resolvedBrief,
        tracker.structureChanged);
    Map<String, Object> restoredMetadata = restoreMetadata(
        snapshot.basePlan().metadata(),
        resolvedBrief,
        tracker.planTextChanged || tracker.structureChanged || tracker.problemsChanged);
    LearningPlanDraftPlan semanticPlan = new LearningPlanDraftPlan(
        title,
        summary,
        resolvedBrief.intent(),
        resolvedBrief.objective(),
        resolvedBrief.durationWeeks(),
        resolvedBrief.level(),
        resolvedBrief.weeklyHours(),
        resolvedBrief.programmingLanguage(),
        resolvedBrief.difficultyDistribution(),
        resolvedBrief.topicPreferences(),
        resolvedBrief.additionalConstraints(),
        restoredPhases,
        restoredMetadata);
    LearningPlanDraftPlan canonicalPlan = withRestoredLoadMetadata(semanticPlan, snapshot.basePlan().metadata());
    validator.validateConfirmablePlan(canonicalPlan);
    return new Compilation(
        resolvedBrief,
        canonicalPlan,
        List.copyOf(replacements),
        problemCount(snapshot.basePlan()),
        problemCount(canonicalPlan),
        difficultyCounts(snapshot.basePlan()),
        difficultyCounts(canonicalPlan),
        tracker.changedPhaseRefs(),
        tracker.briefChanged,
        tracker.structureChanged || tracker.problemsChanged);
  }

  private LearningPlanBrief mergeBrief(LearningPlanBrief base, JsonNode patch) {
    if (!patch.isObject()) {
      throw compilationError("INVALID_BRIEF_PATCH", "briefPatch", "briefPatch 必须是 JSON object。");
    }
    LearningPlanIntent intent = enumValue(patch, "intent", LearningPlanIntent.class, base.intent());
    String objective = patchedText(patch, "objective", base.objective());
    Integer durationWeeks = patchedInteger(patch, "durationWeeks", base.durationWeeks());
    LearningPlanLevel level = enumValue(patch, "level", LearningPlanLevel.class, base.level());
    Integer weeklyHours = patchedInteger(patch, "weeklyHours", base.weeklyHours());
    String programmingLanguage = patchedText(patch, "programmingLanguage", base.programmingLanguage());
    LearningPlanDifficultyDistribution distribution = difficultyDistribution(
        patch.path("difficultyDistribution"), base.difficultyDistribution());
    List<String> topics = patchedStrings(patch, "topicPreferences", base.topicPreferences());
    String additionalConstraints = Boolean.TRUE.equals(booleanValue(patch, "clearAdditionalConstraints"))
        ? null
        : patchedText(patch, "additionalConstraints", base.additionalConstraints());
    LearningPlanBrief resolved = new LearningPlanBrief(
        intent,
        objective,
        base.targetProblemCount(),
        durationWeeks,
        level,
        weeklyHours,
        programmingLanguage,
        distribution,
        topics,
        additionalConstraints,
        base.personalizationEnabled(),
        base.contentLocale());
    List<String> missing = validator.missingRequiredFields(resolved);
    if (!missing.isEmpty()) {
      throw compilationError(
          "INVALID_BRIEF",
          "briefPatch",
          "修订后的 Brief 无效：" + String.join(", ", missing));
    }
    return resolved;
  }

  private void applyPhaseChanges(
      List<MutablePhase> phases,
      JsonNode changes,
      LearningPlanBrief brief,
      List<ReplacementResolution> replacements,
      ChangeTracker tracker
  ) {
    if (!changes.isArray()) {
      throw compilationError("INVALID_PHASE_CHANGES", "planPatch.phaseChanges", "phaseChanges 必须是 array。");
    }
    int changeIndex = 0;
    for (JsonNode change : changes) {
      String path = "planPatch.phaseChanges[" + changeIndex++ + "]";
      String operation = requiredText(change, "operation", path).toUpperCase(Locale.ROOT);
      switch (operation) {
        case "UPDATE" -> updatePhase(phases, change, path, brief, replacements, tracker);
        case "ADD" -> addPhase(phases, change, path, brief, replacements, tracker);
        case "REMOVE" -> removePhase(phases, change, path, tracker);
        case "MOVE" -> movePhase(phases, change, path, tracker);
        default -> throw compilationError("INVALID_PHASE_OPERATION", path + ".operation", "不支持的阶段操作：" + operation);
      }
    }
  }

  private void updatePhase(
      List<MutablePhase> phases,
      JsonNode change,
      String path,
      LearningPlanBrief brief,
      List<ReplacementResolution> replacements,
      ChangeTracker tracker
  ) {
    MutablePhase phase = requirePhase(phases, requiredText(change, "phaseRef", path), path);
    phase.title = patchedText(change, "title", phase.title);
    phase.focus = patchedText(change, "focus", phase.focus);
    applyProblemChanges(phases, phase, change.path("problemChanges"), path, brief, replacements, tracker);
    tracker.changePhase(phase.ref);
  }

  private void addPhase(
      List<MutablePhase> phases,
      JsonNode change,
      String path,
      LearningPlanBrief brief,
      List<ReplacementResolution> replacements,
      ChangeTracker tracker
  ) {
    String ref = "new-phase-" + (tracker.newPhaseCount++ + 1);
    MutablePhase phase = new MutablePhase(
        ref,
        requiredText(change, "title", path),
        textOrNull(change, "focus"),
        1,
        new ArrayList<>());
    insertPhase(phases, phase, textOrNull(change, "beforePhaseRef"), textOrNull(change, "afterPhaseRef"), path);
    applyProblemChanges(phases, phase, change.path("problemChanges"), path, brief, replacements, tracker);
    tracker.structureChanged = true;
    tracker.changePhase(ref);
  }

  private void removePhase(List<MutablePhase> phases, JsonNode change, String path, ChangeTracker tracker) {
    MutablePhase phase = requirePhase(phases, requiredText(change, "phaseRef", path), path);
    phases.remove(phase);
    tracker.structureChanged = true;
    tracker.changePhase(phase.ref);
  }

  private void movePhase(List<MutablePhase> phases, JsonNode change, String path, ChangeTracker tracker) {
    MutablePhase phase = requirePhase(phases, requiredText(change, "phaseRef", path), path);
    phases.remove(phase);
    insertPhase(phases, phase, textOrNull(change, "beforePhaseRef"), textOrNull(change, "afterPhaseRef"), path);
    tracker.structureChanged = true;
    tracker.changePhase(phase.ref);
  }

  private void insertPhase(
      List<MutablePhase> phases,
      MutablePhase phase,
      String beforeRef,
      String afterRef,
      String path
  ) {
    if (beforeRef != null && afterRef != null) {
      throw compilationError("AMBIGUOUS_PHASE_POSITION", path, "beforePhaseRef 与 afterPhaseRef 不能同时提供。");
    }
    if (beforeRef != null) {
      int index = phaseIndex(phases, beforeRef);
      if (index < 0) {
        throw compilationError("PHASE_NOT_FOUND", path + ".beforePhaseRef", "找不到目标阶段：" + beforeRef);
      }
      phases.add(index, phase);
      return;
    }
    if (afterRef != null) {
      int index = phaseIndex(phases, afterRef);
      if (index < 0) {
        throw compilationError("PHASE_NOT_FOUND", path + ".afterPhaseRef", "找不到目标阶段：" + afterRef);
      }
      phases.add(index + 1, phase);
      return;
    }
    phases.add(phase);
  }

  private void applyProblemChanges(
      List<MutablePhase> phases,
      MutablePhase phase,
      JsonNode changes,
      String phasePath,
      LearningPlanBrief brief,
      List<ReplacementResolution> replacements,
      ChangeTracker tracker
  ) {
    if (!changes.isArray()) {
      throw compilationError("INVALID_PROBLEM_CHANGES", phasePath + ".problemChanges", "problemChanges 必须是 array。");
    }
    int problemIndex = 0;
    for (JsonNode change : changes) {
      String path = phasePath + ".problemChanges[" + problemIndex++ + "]";
      String operation = requiredText(change, "operation", path).toUpperCase(Locale.ROOT);
      switch (operation) {
        case "ADD" -> addProblem(phases, phase, change, path, brief, replacements);
        case "REMOVE" -> removeProblem(phase, change, path);
        case "MOVE" -> moveProblem(phases, phase, change, path);
        case "REPLACE" -> replaceProblem(phases, phase, change, path, brief, replacements);
        default -> throw compilationError("INVALID_PROBLEM_OPERATION", path + ".operation", "不支持的题目操作：" + operation);
      }
      tracker.problemsChanged = true;
      tracker.changePhase(phase.ref);
    }
  }

  private void addProblem(
      List<MutablePhase> phases,
      MutablePhase phase,
      JsonNode change,
      String path,
      LearningPlanBrief brief,
      List<ReplacementResolution> replacements
  ) {
    MutableProblem problem = resolveNewProblem(phases, change, path, brief, null, replacements);
    insertProblem(phase.problems, problem, textOrNull(change, "beforeSlug"), textOrNull(change, "afterSlug"), path);
  }

  private void removeProblem(MutablePhase phase, JsonNode change, String path) {
    String slug = requiredText(change, "slug", path);
    MutableProblem problem = requireProblem(phase, slug, path);
    phase.problems.remove(problem);
  }

  private void moveProblem(List<MutablePhase> phases, MutablePhase phase, JsonNode change, String path) {
    String slug = requiredText(change, "slug", path);
    MutableProblem problem = requireProblem(phase, slug, path);
    String targetRef = textOrNull(change, "targetPhaseRef");
    MutablePhase target = targetRef == null ? phase : requirePhase(phases, targetRef, path);
    phase.problems.remove(problem);
    insertProblem(target.problems, problem, textOrNull(change, "beforeSlug"), textOrNull(change, "afterSlug"), path);
  }

  private void replaceProblem(
      List<MutablePhase> phases,
      MutablePhase phase,
      JsonNode change,
      String path,
      LearningPlanBrief brief,
      List<ReplacementResolution> replacements
  ) {
    String removedSlug = requiredText(change, "slug", path);
    MutableProblem existing = requireProblem(phase, removedSlug, path);
    int index = phase.problems.indexOf(existing);
    MutableProblem replacement = resolveNewProblem(phases, change, path, brief, removedSlug, replacements);
    phase.problems.set(index, replacement);
  }

  private MutableProblem resolveNewProblem(
      List<MutablePhase> phases,
      JsonNode change,
      String path,
      LearningPlanBrief brief,
      String removedSlug,
      List<ReplacementResolution> replacements
  ) {
    String directSlug = removedSlug == null ? textOrNull(change, "slug") : textOrNull(change, "replacementSlug");
    String directReason = removedSlug == null ? textOrNull(change, "reason") : textOrNull(change, "replacementReason");
    if (directSlug != null) {
      if (directReason == null) {
        throw compilationError("REASON_REQUIRED", path, "新增或替换题目必须提供 reason。");
      }
      ensureNotDuplicate(phases, directSlug, removedSlug, path);
      return new MutableProblem(directSlug, directReason);
    }

    JsonNode selection = change.path("replacementSelection");
    if (!selection.isObject()) {
      throw compilationError("REPLACEMENT_REQUIRED", path, "必须提供精确 slug 或 replacementSelection。");
    }
    String difficulty = requiredText(selection, "difficulty", path + ".replacementSelection")
        .toUpperCase(Locale.ROOT);
    String learningGoal = requiredText(selection, "learningGoal", path + ".replacementSelection");
    String keyword = textOrNull(selection, "keyword");
    List<String> topicHints = strings(selection.path("topicHints"));
    Set<String> excluded = allSlugs(phases);
    if (removedSlug != null) {
      excluded.remove(removedSlug);
    }
    LearningPlanProblemCandidate candidate = selectCandidate(
        difficulty,
        keyword,
        topicHints,
        excluded,
        brief.contentLocale(),
        path);
    String reason = nonBlank(candidate.recommendationReason()) == null
        ? learningGoal
        : candidate.recommendationReason().trim();
    replacements.add(new ReplacementResolution(
        removedSlug,
        candidate.slug(),
        displayTitle(candidate, brief.contentLocale()),
        candidate.difficulty(),
        reason));
    return new MutableProblem(candidate.slug(), reason);
  }

  private LearningPlanProblemCandidate selectCandidate(
      String difficulty,
      String keyword,
      List<String> topicHints,
      Set<String> excluded,
      LearningPlanContentLocale locale,
      String path
  ) {
    List<LearningPlanProblemSearch> searches = new ArrayList<>();
    for (String hint : topicHints) {
      problemCatalog.findCanonicalTagValue(hint, locale.languageTag())
          .ifPresent(tag -> searches.add(new LearningPlanProblemSearch(keyword, difficulty, tag, MAX_CANDIDATES)));
    }
    if (keyword != null) {
      searches.add(new LearningPlanProblemSearch(keyword, difficulty, null, MAX_CANDIDATES));
    }
    searches.add(new LearningPlanProblemSearch(null, difficulty, null, MAX_CANDIDATES));
    Set<String> visited = new HashSet<>();
    for (LearningPlanProblemSearch search : searches) {
      for (LearningPlanProblemCandidate candidate : problemCatalog.searchProblems(search)) {
        if (candidate.slug() == null || !visited.add(candidate.slug()) || excluded.contains(candidate.slug())) {
          continue;
        }
        if (!difficulty.equalsIgnoreCase(candidate.difficulty())) {
          continue;
        }
        return candidate;
      }
    }
    throw compilationError(
        "NO_MATCHING_REPLACEMENT",
        path,
        "没有找到满足当前难度、主题且未在计划中出现的替代题目。");
  }

  private void insertProblem(
      List<MutableProblem> problems,
      MutableProblem problem,
      String beforeSlug,
      String afterSlug,
      String path
  ) {
    if (beforeSlug != null && afterSlug != null) {
      throw compilationError("AMBIGUOUS_PROBLEM_POSITION", path, "beforeSlug 与 afterSlug 不能同时提供。");
    }
    if (beforeSlug != null) {
      int index = problemIndex(problems, beforeSlug);
      if (index < 0) {
        throw compilationError("PROBLEM_NOT_FOUND", path + ".beforeSlug", "找不到定位题目：" + beforeSlug);
      }
      problems.add(index, problem);
      return;
    }
    if (afterSlug != null) {
      int index = problemIndex(problems, afterSlug);
      if (index < 0) {
        throw compilationError("PROBLEM_NOT_FOUND", path + ".afterSlug", "找不到定位题目：" + afterSlug);
      }
      problems.add(index + 1, problem);
      return;
    }
    problems.add(problem);
  }

  private List<LearningPlanPhaseDraft> hydrateAndRebuild(
      List<MutablePhase> phases,
      LearningPlanRevisionBaseSnapshot snapshot,
      LearningPlanBrief brief,
      boolean structureChanged
  ) {
    int[] durations = durations(phases, snapshot.basePlan(), brief.durationWeeks(), structureChanged);
    Set<String> slugs = new HashSet<>();
    List<LearningPlanPhaseDraft> result = new ArrayList<>();
    for (int phaseIndex = 0; phaseIndex < phases.size(); phaseIndex++) {
      MutablePhase phase = phases.get(phaseIndex);
      List<LearningPlanProblemDraft> problems = new ArrayList<>();
      int sortOrder = 1;
      for (MutableProblem problem : phase.problems) {
        if (!slugs.add(problem.slug)) {
          throw compilationError("DUPLICATE_PROBLEM", "planPatch", "修订后题目重复：" + problem.slug);
        }
        LearningPlanProblemCandidate candidate = problemCatalog.findBySlug(
                problem.slug, brief.contentLocale().languageTag())
            .orElseThrow(() -> compilationError(
                "PROBLEM_NOT_FOUND", "planPatch", "本地题库不存在题目：" + problem.slug));
        problems.add(LearningPlanProblemDraft.fromCandidate(candidate, sortOrder++, problem.reason));
      }
      result.add(new LearningPlanPhaseDraft(
          phaseIndex + 1,
          phase.title,
          durations[phaseIndex],
          phase.focus,
          problems));
    }
    return List.copyOf(result);
  }

  private int[] durations(
      List<MutablePhase> phases,
      LearningPlanDraftPlan basePlan,
      int durationWeeks,
      boolean structureChanged
  ) {
    boolean canPreserve = !structureChanged
        && durationWeeks == basePlan.durationWeeks()
        && phases.size() == basePlan.phases().size();
    int[] result = new int[phases.size()];
    if (canPreserve) {
      for (int index = 0; index < phases.size(); index++) {
        result[index] = phases.get(index).durationWeeks;
      }
      return result;
    }
    int base = durationWeeks / phases.size();
    int remainder = durationWeeks % phases.size();
    for (int index = 0; index < phases.size(); index++) {
      result[index] = base + (index < remainder ? 1 : 0);
    }
    return result;
  }

  private Map<String, Object> restoreMetadata(
      Map<String, Object> baseMetadata,
      LearningPlanBrief brief,
      boolean contentChanged
  ) {
    Map<String, Object> metadata = new LinkedHashMap<>(baseMetadata);
    metadata.put(LearningPlanDraftMetadataKeys.CONTENT_LOCALE, brief.contentLocale().languageTag());
    metadata.put(LearningPlanDraftMetadataKeys.PERSONALIZATION_ENABLED, brief.personalizationEnabled());
    metadata.remove(LearningPlanDraftMetadataKeys.LOAD_SUMMARY);
    if (contentChanged) {
      Object template = metadata.get(LearningPlanDraftMetadataKeys.TEMPLATE);
      if (template instanceof Map<?, ?> values) {
        Map<String, Object> provenance = new LinkedHashMap<>();
        values.forEach((key, value) -> provenance.put(String.valueOf(key), value));
        provenance.remove(LearningPlanDraftMetadataKeys.MATCHED_PROBLEM_COUNT);
        metadata.put(LearningPlanDraftMetadataKeys.TEMPLATE, Map.copyOf(provenance));
      }
    }
    return Map.copyOf(metadata);
  }

  private LearningPlanDraftPlan withRestoredLoadMetadata(
      LearningPlanDraftPlan plan,
      Map<String, Object> baseMetadata
  ) {
    LearningPlanCoveragePolicy policy = coveragePolicy(baseMetadata);
    LearningPlanDraftPlan defaults = loadService.withLoadMetadata(plan, policy);
    int daily = metadataInt(
        baseMetadata.get(LearningPlanDraftMetadataKeys.DAILY_PROBLEM_COUNT),
        defaults.metadata().get(LearningPlanDraftMetadataKeys.DAILY_PROBLEM_COUNT));
    int days = metadataInt(
        baseMetadata.get(LearningPlanDraftMetadataKeys.TRAINING_DAYS_PER_WEEK),
        defaults.metadata().get(LearningPlanDraftMetadataKeys.TRAINING_DAYS_PER_WEEK));
    return loadService.withRhythmMetadata(plan, daily, days, policy);
  }

  private LearningPlanCoveragePolicy coveragePolicy(Map<String, Object> metadata) {
    Object value = metadata.get(LearningPlanDraftMetadataKeys.COVERAGE_POLICY);
    if (value instanceof String text) {
      try {
        return LearningPlanCoveragePolicy.valueOf(text);
      } catch (IllegalArgumentException ignored) {
        return LearningPlanCoveragePolicy.FIT_USER_BUDGET;
      }
    }
    return LearningPlanCoveragePolicy.FIT_USER_BUDGET;
  }

  private int metadataInt(Object preferred, Object fallback) {
    Object value = preferred == null ? fallback : preferred;
    if (value instanceof Number number) {
      return number.intValue();
    }
    return Integer.parseInt(String.valueOf(value));
  }

  private List<MutablePhase> mutablePhases(LearningPlanDraftPlan plan) {
    List<MutablePhase> phases = new ArrayList<>();
    for (int index = 0; index < plan.phases().size(); index++) {
      LearningPlanPhaseDraft phase = plan.phases().get(index);
      List<MutableProblem> problems = phase.problems().stream()
          .map(problem -> new MutableProblem(problem.slug(), problem.reason()))
          .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
      phases.add(new MutablePhase(
          LearningPlanRevisionModelViewProjector.phaseRef(index),
          phase.title(),
          phase.focus(),
          phase.durationWeeks(),
          problems));
    }
    return phases;
  }

  private LearningPlanDifficultyDistribution difficultyDistribution(
      JsonNode node,
      LearningPlanDifficultyDistribution fallback
  ) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return fallback;
    }
    if (!node.isObject()) {
      throw compilationError(
          "INVALID_DIFFICULTY_DISTRIBUTION",
          "briefPatch.difficultyDistribution",
          "difficultyDistribution 必须是 JSON object。");
    }
    return new LearningPlanDifficultyDistribution(
        requiredInteger(node, "easyPercent", "briefPatch.difficultyDistribution"),
        requiredInteger(node, "mediumPercent", "briefPatch.difficultyDistribution"),
        requiredInteger(node, "hardPercent", "briefPatch.difficultyDistribution"));
  }

  private <T extends Enum<T>> T enumValue(JsonNode node, String field, Class<T> type, T fallback) {
    String value = textOrNull(node, field);
    if (value == null) {
      return fallback;
    }
    try {
      return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw compilationError("INVALID_ENUM", "briefPatch." + field, "无效的 " + field + "：" + value);
    }
  }

  private String patchedText(JsonNode node, String field, String fallback) {
    String value = textOrNull(node, field);
    return value == null ? fallback : value;
  }

  private Integer patchedInteger(JsonNode node, String field, Integer fallback) {
    JsonNode value = node.get(field);
    if (value == null || value.isNull()) {
      return fallback;
    }
    if (!value.isIntegralNumber()) {
      throw compilationError("INVALID_INTEGER", "briefPatch." + field, field + " 必须是整数。");
    }
    return value.intValue();
  }

  private List<String> patchedStrings(JsonNode node, String field, List<String> fallback) {
    JsonNode value = node.get(field);
    return value == null || value.isNull() ? fallback : strings(value);
  }

  private List<String> strings(JsonNode node) {
    if (!node.isArray()) {
      throw compilationError("INVALID_STRING_ARRAY", "patch", "字段必须是字符串数组。");
    }
    List<String> result = new ArrayList<>();
    for (JsonNode item : node) {
      if (!item.isTextual() || item.textValue().isBlank()) {
        throw compilationError("INVALID_STRING_ARRAY", "patch", "数组元素必须是非空字符串。");
      }
      result.add(item.textValue().trim());
    }
    return List.copyOf(result);
  }

  private Boolean booleanValue(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || value.isNull()) {
      return null;
    }
    if (!value.isBoolean()) {
      throw compilationError("INVALID_BOOLEAN", "briefPatch." + field, field + " 必须是 boolean。");
    }
    return value.booleanValue();
  }

  private String requiredText(JsonNode node, String field, String path) {
    String value = textOrNull(node, field);
    if (value == null) {
      throw compilationError("REQUIRED_FIELD_MISSING", path + "." + field, field + " 不能为空。");
    }
    return value;
  }

  private int requiredInteger(JsonNode node, String field, String path) {
    JsonNode value = node.get(field);
    if (value == null || !value.isIntegralNumber()) {
      throw compilationError("REQUIRED_FIELD_MISSING", path + "." + field, field + " 必须是整数。");
    }
    return value.intValue();
  }

  private String textOrNull(JsonNode node, String field) {
    if (node == null || !node.isObject()) {
      return null;
    }
    JsonNode value = node.get(field);
    return value == null || value.isNull() || !value.isTextual() || value.textValue().isBlank()
        ? null
        : value.textValue().trim();
  }

  private MutablePhase requirePhase(List<MutablePhase> phases, String ref, String path) {
    return phases.stream()
        .filter(phase -> phase.ref.equals(ref))
        .findFirst()
        .orElseThrow(() -> compilationError("PHASE_NOT_FOUND", path + ".phaseRef", "找不到阶段：" + ref));
  }

  private MutableProblem requireProblem(MutablePhase phase, String slug, String path) {
    return phase.problems.stream()
        .filter(problem -> problem.slug.equals(slug))
        .findFirst()
        .orElseThrow(() -> compilationError("PROBLEM_NOT_FOUND", path + ".slug", "阶段中找不到题目：" + slug));
  }

  private void ensureNotDuplicate(List<MutablePhase> phases, String slug, String ignoredSlug, String path) {
    boolean duplicate = phases.stream()
        .flatMap(phase -> phase.problems.stream())
        .map(problem -> problem.slug)
        .anyMatch(existing -> existing.equals(slug) && !existing.equals(ignoredSlug));
    if (duplicate) {
      throw compilationError("DUPLICATE_PROBLEM", path, "题目已存在于当前计划：" + slug);
    }
  }

  private Set<String> allSlugs(List<MutablePhase> phases) {
    Set<String> result = new LinkedHashSet<>();
    phases.forEach(phase -> phase.problems.forEach(problem -> result.add(problem.slug)));
    return result;
  }

  private int phaseIndex(List<MutablePhase> phases, String ref) {
    for (int index = 0; index < phases.size(); index++) {
      if (phases.get(index).ref.equals(ref)) {
        return index;
      }
    }
    return -1;
  }

  private int problemIndex(List<MutableProblem> problems, String slug) {
    for (int index = 0; index < problems.size(); index++) {
      if (problems.get(index).slug.equals(slug)) {
        return index;
      }
    }
    return -1;
  }

  private int problemCount(LearningPlanDraftPlan plan) {
    return plan.phases().stream().mapToInt(phase -> phase.problems().size()).sum();
  }

  private DifficultyCounts difficultyCounts(LearningPlanDraftPlan plan) {
    int easy = 0;
    int medium = 0;
    int hard = 0;
    for (LearningPlanPhaseDraft phase : plan.phases()) {
      for (LearningPlanProblemDraft problem : phase.problems()) {
        switch (LearningPlanRevisionModelViewProjector.normalizeDifficulty(problem.difficulty())) {
          case "EASY" -> easy++;
          case "HARD" -> hard++;
          default -> medium++;
        }
      }
    }
    return new DifficultyCounts(easy, medium, hard);
  }

  private String displayTitle(LearningPlanProblemCandidate candidate, LearningPlanContentLocale locale) {
    String preferred = locale == LearningPlanContentLocale.EN_US ? candidate.title() : candidate.titleCn();
    if (nonBlank(preferred) != null) {
      return preferred;
    }
    String fallback = locale == LearningPlanContentLocale.EN_US ? candidate.titleCn() : candidate.title();
    return nonBlank(fallback) == null ? candidate.slug() : fallback;
  }

  private String nonBlank(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private LearningPlanRevisionCompilationException compilationError(String code, String path, String message) {
    return new LearningPlanRevisionCompilationException(new Diagnostic(code, path, message));
  }

  public record Compilation(
      LearningPlanBrief resolvedBrief,
      LearningPlanDraftPlan canonicalPlan,
      List<ReplacementResolution> replacementResolutions,
      int problemCountBefore,
      int problemCountAfter,
      DifficultyCounts difficultyCountsBefore,
      DifficultyCounts difficultyCountsAfter,
      List<String> changedPhaseRefs,
      boolean briefChanged,
      boolean contentChanged
  ) {
  }

  public record DifficultyCounts(int easy, int medium, int hard) {
  }

  public record ReplacementResolution(
      String removedSlug,
      String selectedSlug,
      String displayTitle,
      String difficulty,
      String reason
  ) {
  }

  public record Diagnostic(String code, String path, String message) {
  }

  public static final class LearningPlanRevisionCompilationException extends LearningPlanException {

    private final Diagnostic diagnostic;

    public LearningPlanRevisionCompilationException(Diagnostic diagnostic) {
      super("LEARNING_PLAN_REVISION_COMPILE_INVALID", diagnostic.message());
      this.diagnostic = diagnostic;
    }

    public Diagnostic diagnostic() {
      return diagnostic;
    }
  }

  private static final class MutablePhase {
    private final String ref;
    private String title;
    private String focus;
    private final int durationWeeks;
    private final List<MutableProblem> problems;

    private MutablePhase(
        String ref,
        String title,
        String focus,
        int durationWeeks,
        List<MutableProblem> problems
    ) {
      this.ref = ref;
      this.title = title;
      this.focus = focus;
      this.durationWeeks = durationWeeks;
      this.problems = problems;
    }
  }

  private record MutableProblem(String slug, String reason) {
  }

  private static final class ChangeTracker {
    private final LinkedHashSet<String> changedPhases = new LinkedHashSet<>();
    private boolean briefChanged;
    private boolean planTextChanged;
    private boolean structureChanged;
    private boolean problemsChanged;
    private int newPhaseCount;

    private void changePhase(String ref) {
      changedPhases.add(ref);
    }

    private List<String> changedPhaseRefs() {
      return List.copyOf(changedPhases);
    }
  }
}
