package org.congcong.algomentor.mentor.application.learningplan.proposal.revision;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.congcong.algomentor.agent.core.AgentExecutionContext;
import org.congcong.algomentor.agent.core.AgentTool;
import org.congcong.algomentor.llm.core.tool.LlmToolSpec;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanPhaseDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemDraft;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanDraftRevision;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalRepository;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalRevisionStatus;
import org.congcong.algomentor.mentor.application.learningplan.proposal.revision.LearningPlanRevisionBaselineResolver.BaselineResolutionException;
import org.congcong.algomentor.mentor.application.learningplan.proposal.revision.LearningPlanRevisionBaselineResolver.ResolvedBaseline;
import org.congcong.algomentor.mentor.application.learningplan.proposal.revision.LearningPlanRevisionCanonicalRestorer.Compilation;
import org.congcong.algomentor.mentor.application.learningplan.proposal.revision.LearningPlanRevisionCanonicalRestorer.Diagnostic;
import org.congcong.algomentor.mentor.application.learningplan.proposal.revision.LearningPlanRevisionCanonicalRestorer.DifficultyCounts;
import org.congcong.algomentor.mentor.application.learningplan.proposal.revision.LearningPlanRevisionCanonicalRestorer.LearningPlanRevisionCompilationException;
import org.congcong.algomentor.mentor.application.learningplan.proposal.revision.LearningPlanRevisionCanonicalRestorer.ReplacementResolution;

/** 编译语义修订 Patch，暂存完整 proposed Brief 与 canonical plan。 */
public final class CompileLearningPlanRevisionAgentTool implements AgentTool {

  private static final LlmToolSpec SPEC = new LlmToolSpec(
      LearningPlanRevisionToolContracts.COMPILE_TOOL_NAME,
      "Compile a semantic patch against a trusted immutable baseline of the active learning-plan revision. Select "
          + "ORIGINAL_DRAFT to restore the first complete draft regardless of whether it came from a template or AI, "
          + "or PREVIOUS_REVISION to undo the last revision. The server restores canonical fields and stores a bounded "
          + "proposed artifact. Always use this tool for the final revision; never return a full plan.",
      inputSchema(),
      true);

  private final LearningPlanProposalRepository proposalRepository;
  private final LearningPlanRevisionBaselineResolver baselineResolver;
  private final LearningPlanRevisionCanonicalRestorer restorer;
  private final Clock clock;

  public CompileLearningPlanRevisionAgentTool(
      LearningPlanProposalRepository proposalRepository,
      LearningPlanRevisionCanonicalRestorer restorer
  ) {
    this(
        proposalRepository,
        new LearningPlanRevisionBaselineResolver(proposalRepository),
        restorer,
        Clock.systemUTC());
  }

  public CompileLearningPlanRevisionAgentTool(
      LearningPlanProposalRepository proposalRepository,
      LearningPlanRevisionCanonicalRestorer restorer,
      Clock clock
  ) {
    this(
        proposalRepository,
        new LearningPlanRevisionBaselineResolver(proposalRepository),
        restorer,
        clock);
  }

  public CompileLearningPlanRevisionAgentTool(
      LearningPlanProposalRepository proposalRepository,
      LearningPlanRevisionBaselineResolver baselineResolver,
      LearningPlanRevisionCanonicalRestorer restorer,
      Clock clock
  ) {
    this.proposalRepository = Objects.requireNonNull(proposalRepository, "Proposal repository must not be null");
    this.baselineResolver = Objects.requireNonNull(baselineResolver, "Baseline resolver must not be null");
    this.restorer = Objects.requireNonNull(restorer, "Learning plan revision restorer must not be null");
    this.clock = Objects.requireNonNull(clock, "Clock must not be null");
  }

  @Override
  public LlmToolSpec spec() {
    return SPEC;
  }

  @Override
  public JsonNode execute(JsonNode arguments, AgentExecutionContext context) {
    LearningPlanRevisionToolContext.TrustedContext trusted = LearningPlanRevisionToolContext.resolve(context);
    if (trusted.failureCode() != null) {
      return needsRevision(new Diagnostic(
          trusted.failureCode(),
          "trustedContext",
          "缺少有效的学习计划修订运行上下文。"));
    }
    LearningPlanDraftRevision revision = proposalRepository.findDraftRevisionForUser(
            trusted.revisionId(), trusted.userId())
        .orElse(null);
    if (revision == null || revision.baseBrief() == null || revision.basePlan() == null) {
      return needsRevision(new Diagnostic(
          "REVISION_SNAPSHOT_NOT_FOUND",
          "trustedContext",
          "找不到当前修订的冻结快照。"));
    }
    if (revision.status() != LearningPlanProposalRevisionStatus.GENERATING) {
      return needsRevision(new Diagnostic(
          "REVISION_NOT_GENERATING",
          "trustedContext",
          "当前修订已不再接受编译结果。"));
    }
    try {
      ResolvedBaseline resolved = baselineResolver.resolve(
          revision,
          trusted.userId(),
          textOrNull(arguments, "baseline"));
      Compilation compilation = restorer.compile(
          resolved.snapshot(),
          arguments);
      LearningPlanDraftRevision saved = proposalRepository.saveDraftRevision(revision.withCompiled(
          compilation.resolvedBrief(),
          compilation.canonicalPlan(),
          clock.instant()));
      return pass(saved, resolved.baseline(), compilation);
    } catch (BaselineResolutionException exception) {
      return needsRevision(new Diagnostic(exception.code(), exception.path(), exception.getMessage()));
    } catch (LearningPlanRevisionCompilationException exception) {
      return needsRevision(exception.diagnostic());
    } catch (RuntimeException exception) {
      return needsRevision(new Diagnostic(
          "COMPILATION_FAILED",
          "patch",
          "学习计划修订编译失败，请调整 Patch 后重试。"));
    }
  }

  private ObjectNode pass(
      LearningPlanDraftRevision revision,
      String baseline,
      Compilation compilation
  ) {
    boolean changed = !revision.baseBrief().equals(compilation.resolvedBrief())
        || !revision.basePlan().equals(compilation.canonicalPlan());
    ObjectNode result = JsonNodeFactory.instance.objectNode();
    result.put("status", LearningPlanRevisionToolContracts.STATUS_PASS);
    result.put("artifactRef", LearningPlanRevisionToolContracts.artifactRef(revision.id()));
    result.put("baseline", baseline);
    result.put("changed", changed);
    ObjectNode summary = result.putObject("changeSummary");
    summary.put("briefChanged", !revision.baseBrief().equals(compilation.resolvedBrief()));
    ArrayNode changedPhases = summary.putArray("changedPhaseRefs");
    changedPhaseRefs(revision.basePlan(), compilation.canonicalPlan()).forEach(changedPhases::add);
    summary.put("problemCountBefore", problemCount(revision.basePlan()));
    summary.put("problemCountAfter", compilation.problemCountAfter());
    summary.set("difficultyCountsBefore", difficultyCounts(difficultyCounts(revision.basePlan())));
    summary.set("difficultyCountsAfter", difficultyCounts(compilation.difficultyCountsAfter()));
    ArrayNode replacements = result.putArray("replacementResolutions");
    compilation.replacementResolutions().forEach(item -> replacements.add(replacement(item)));
    ObjectNode restoration = result.putObject("restorationSummary");
    restoration.put("preservedBriefFields", 11);
    restoration.put("hydratedProblemCount", compilation.problemCountAfter());
    restoration.put("rebuiltPhaseIndexes", compilation.canonicalPlan().phases().size());
    restoration.put("rebuiltSortOrders", compilation.problemCountAfter());
    restoration.put("compatibilityPhaseDurationsRestored", true);
    restoration.put("metadataLoadSummaryRecalculated", true);
    restoration.put("templateMatchAssertionInvalidated", compilation.contentChanged());
    return result;
  }

  private List<String> changedPhaseRefs(LearningPlanDraftPlan before, LearningPlanDraftPlan after) {
    int phaseCount = Math.max(before.phases().size(), after.phases().size());
    List<String> refs = new ArrayList<>();
    for (int index = 0; index < phaseCount; index++) {
      LearningPlanPhaseDraft beforePhase = index < before.phases().size() ? before.phases().get(index) : null;
      LearningPlanPhaseDraft afterPhase = index < after.phases().size() ? after.phases().get(index) : null;
      if (!Objects.equals(beforePhase, afterPhase)) {
        refs.add(LearningPlanRevisionModelViewProjector.phaseRef(index));
      }
    }
    return List.copyOf(refs);
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

  private ObjectNode replacement(ReplacementResolution value) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    putNullable(node, "removedSlug", value.removedSlug());
    node.put("selectedSlug", value.selectedSlug());
    node.put("displayTitle", value.displayTitle());
    putNullable(node, "difficulty", value.difficulty());
    node.put("reason", value.reason());
    return node;
  }

  private ObjectNode difficultyCounts(DifficultyCounts counts) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("easy", counts.easy());
    node.put("medium", counts.medium());
    node.put("hard", counts.hard());
    return node;
  }

  private ObjectNode needsRevision(Diagnostic diagnostic) {
    ObjectNode result = JsonNodeFactory.instance.objectNode();
    result.put("status", LearningPlanRevisionToolContracts.STATUS_NEEDS_REVISION);
    ArrayNode diagnostics = result.putArray("diagnostics");
    ObjectNode node = diagnostics.addObject();
    node.put("code", diagnostic.code());
    node.put("path", diagnostic.path());
    node.put("message", diagnostic.message());
    return result;
  }

  private void putNullable(ObjectNode node, String field, String value) {
    if (value == null) {
      node.putNull(field);
    } else {
      node.put(field, value);
    }
  }

  private String textOrNull(JsonNode arguments, String field) {
    if (arguments == null || !arguments.isObject()) {
      return null;
    }
    JsonNode value = arguments.get(field);
    return value == null || value.isNull() || !value.isTextual() || value.textValue().isBlank()
        ? null
        : value.textValue().trim();
  }

  private static JsonNode inputSchema() {
    ObjectNode root = object();
    root.put("additionalProperties", false);
    ObjectNode properties = root.putObject("properties");
    properties.set("baseline", nullableEnum(
        LearningPlanRevisionToolContracts.BASELINE_CURRENT_REVISION,
        LearningPlanRevisionToolContracts.BASELINE_ORIGINAL_DRAFT,
        LearningPlanRevisionToolContracts.BASELINE_PREVIOUS_REVISION));
    properties.set("briefPatch", briefPatch());
    properties.set("planPatch", planPatch());
    requireAll(root, properties);
    return root;
  }

  private static ObjectNode briefPatch() {
    ObjectNode root = object();
    root.put("additionalProperties", false);
    ObjectNode properties = root.putObject("properties");
    properties.set("intent", nullableEnum(
        "PRACTICE_GOAL", "ABILITY_DIAGNOSIS", "INTERVIEW_SPRINT", "TOPIC_BREAKTHROUGH",
        "MISTAKE_REVIEW", "LONG_TERM_LEARNING"));
    properties.set("objective", nullableString());
    properties.set("durationWeeks", nullableInteger(1, 52));
    properties.set("level", nullableEnum("BEGINNER", "INTERMEDIATE", "ADVANCED"));
    properties.set("weeklyHours", nullableInteger(1, 80));
    properties.set("programmingLanguage", nullableString());
    properties.set("difficultyDistribution", nullableDifficultyDistribution());
    properties.set("topicPreferences", nullableStringArray());
    properties.set("additionalConstraints", nullableString());
    properties.set("clearAdditionalConstraints", nullableBoolean());
    requireAll(root, properties);
    return root;
  }

  private static ObjectNode planPatch() {
    ObjectNode root = object();
    root.put("additionalProperties", false);
    ObjectNode properties = root.putObject("properties");
    properties.set("title", nullableString());
    properties.set("summary", nullableString());
    ObjectNode phaseChanges = array();
    phaseChanges.set("items", phaseChange());
    properties.set("phaseChanges", phaseChanges);
    requireAll(root, properties);
    return root;
  }

  private static ObjectNode phaseChange() {
    ObjectNode root = object();
    root.put("additionalProperties", false);
    ObjectNode properties = root.putObject("properties");
    properties.set("operation", enumString("UPDATE", "ADD", "REMOVE", "MOVE"));
    properties.set("phaseRef", nullableString());
    properties.set("title", nullableString());
    properties.set("focus", nullableString());
    properties.set("beforePhaseRef", nullableString());
    properties.set("afterPhaseRef", nullableString());
    ObjectNode changes = array();
    changes.set("items", problemChange());
    properties.set("problemChanges", changes);
    requireAll(root, properties);
    return root;
  }

  private static ObjectNode problemChange() {
    ObjectNode root = object();
    root.put("additionalProperties", false);
    ObjectNode properties = root.putObject("properties");
    properties.set("operation", enumString("ADD", "REMOVE", "MOVE", "REPLACE"));
    properties.set("slug", nullableString());
    properties.set("reason", nullableString());
    properties.set("replacementSlug", nullableString());
    properties.set("replacementReason", nullableString());
    properties.set("beforeSlug", nullableString());
    properties.set("afterSlug", nullableString());
    properties.set("targetPhaseRef", nullableString());
    properties.set("replacementSelection", nullableReplacementSelection());
    requireAll(root, properties);
    return root;
  }

  private static ObjectNode nullableReplacementSelection() {
    ObjectNode node = object();
    node.putArray("type").removeAll().add("object").add("null");
    node.put("additionalProperties", false);
    ObjectNode properties = node.putObject("properties");
    properties.set("difficulty", nullableEnum("EASY", "MEDIUM", "HARD"));
    properties.set("keyword", nullableString());
    properties.set("topicHints", stringArray());
    properties.set("learningGoal", nullableString());
    requireAll(node, properties);
    return node;
  }

  private static ObjectNode nullableDifficultyDistribution() {
    ObjectNode node = object();
    node.putArray("type").removeAll().add("object").add("null");
    node.put("additionalProperties", false);
    ObjectNode properties = node.putObject("properties");
    properties.set("easyPercent", integer(0, 100));
    properties.set("mediumPercent", integer(0, 100));
    properties.set("hardPercent", integer(0, 100));
    requireAll(node, properties);
    return node;
  }

  private static ObjectNode object() {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("type", "object");
    return node;
  }

  private static ObjectNode array() {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("type", "array");
    return node;
  }

  private static ObjectNode stringArray() {
    ObjectNode node = array();
    ObjectNode item = JsonNodeFactory.instance.objectNode();
    item.put("type", "string");
    node.set("items", item);
    return node;
  }

  private static ObjectNode nullableStringArray() {
    ObjectNode node = stringArray();
    node.putArray("type").removeAll().add("array").add("null");
    return node;
  }

  private static ObjectNode nullableString() {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.putArray("type").add("string").add("null");
    return node;
  }

  private static ObjectNode nullableBoolean() {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.putArray("type").add("boolean").add("null");
    return node;
  }

  private static ObjectNode integer(int minimum, int maximum) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("type", "integer");
    node.put("minimum", minimum);
    node.put("maximum", maximum);
    return node;
  }

  private static ObjectNode nullableInteger(int minimum, int maximum) {
    ObjectNode node = integer(minimum, maximum);
    node.putArray("type").removeAll().add("integer").add("null");
    return node;
  }

  private static ObjectNode enumString(String... values) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("type", "string");
    ArrayNode enums = node.putArray("enum");
    for (String value : values) {
      enums.add(value);
    }
    return node;
  }

  private static ObjectNode nullableEnum(String... values) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.putArray("type").add("string").add("null");
    ArrayNode enums = node.putArray("enum");
    for (String value : values) {
      enums.add(value);
    }
    enums.addNull();
    return node;
  }

  private static void requireAll(ObjectNode root, ObjectNode properties) {
    ArrayNode required = root.putArray("required");
    properties.fieldNames().forEachRemaining(required::add);
  }
}
