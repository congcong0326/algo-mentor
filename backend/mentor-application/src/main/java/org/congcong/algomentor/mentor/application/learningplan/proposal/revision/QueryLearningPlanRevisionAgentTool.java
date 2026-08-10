package org.congcong.algomentor.mentor.application.learningplan.proposal.revision;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.congcong.algomentor.agent.core.AgentExecutionContext;
import org.congcong.algomentor.agent.core.AgentTool;
import org.congcong.algomentor.llm.core.tool.LlmToolSpec;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanContentLocale;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanPhaseDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemDraft;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanDraftRevision;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalRepository;
import org.congcong.algomentor.mentor.application.learningplan.proposal.revision.LearningPlanRevisionBaselineResolver.BaselineOption;
import org.congcong.algomentor.mentor.application.learningplan.proposal.revision.LearningPlanRevisionBaselineResolver.BaselineResolutionException;
import org.congcong.algomentor.mentor.application.learningplan.proposal.revision.LearningPlanRevisionBaselineResolver.ResolvedBaseline;

/** 只读取当前 run 绑定的受信学习计划修订基线。 */
public final class QueryLearningPlanRevisionAgentTool implements AgentTool {

  private static final int DEFAULT_LIMIT = 20;
  private static final int MAX_LIMIT = 30;
  private static final LlmToolSpec SPEC = new LlmToolSpec(
      LearningPlanRevisionToolContracts.QUERY_TOOL_NAME,
      "Read trusted immutable baselines for the active learning-plan revision. Use READ_BASELINE_OPTIONS before "
          + "restore or undo requests, READ_PHASE for paginated phase details, and FIND_PROBLEMS to filter only "
          + "problems already in the selected frozen baseline. This tool never accepts draft, revision, or template "
          + "identifiers and never searches the global catalog.",
      inputSchema(),
      true);

  private final LearningPlanProposalRepository proposalRepository;
  private final LearningPlanRevisionBaselineResolver baselineResolver;

  public QueryLearningPlanRevisionAgentTool(LearningPlanProposalRepository proposalRepository) {
    this(proposalRepository, new LearningPlanRevisionBaselineResolver(proposalRepository));
  }

  public QueryLearningPlanRevisionAgentTool(
      LearningPlanProposalRepository proposalRepository,
      LearningPlanRevisionBaselineResolver baselineResolver
  ) {
    this.proposalRepository = Objects.requireNonNull(proposalRepository, "Proposal repository must not be null");
    this.baselineResolver = Objects.requireNonNull(baselineResolver, "Baseline resolver must not be null");
  }

  @Override
  public LlmToolSpec spec() {
    return SPEC;
  }

  @Override
  public JsonNode execute(JsonNode arguments, AgentExecutionContext context) {
    LearningPlanRevisionToolContext.TrustedContext trusted = LearningPlanRevisionToolContext.resolve(context);
    if (trusted.failureCode() != null) {
      return failure(trusted.failureCode(), "缺少有效的学习计划修订运行上下文。");
    }
    LearningPlanDraftRevision revision = proposalRepository.findDraftRevisionForUser(
            trusted.revisionId(), trusted.userId())
        .orElse(null);
    if (revision == null || revision.baseBrief() == null || revision.basePlan() == null) {
      return failure("REVISION_SNAPSHOT_NOT_FOUND", "找不到当前修订的冻结快照。");
    }
    try {
      String operation = requiredText(arguments, "operation").toUpperCase(Locale.ROOT);
      return switch (operation) {
        case LearningPlanRevisionToolContracts.OPERATION_READ_BASELINE_OPTIONS ->
            readBaselineOptions(revision, trusted.userId());
        case LearningPlanRevisionToolContracts.OPERATION_READ_PHASE -> readPhase(
            revision,
            baselineResolver.resolve(revision, trusted.userId(), textOrNull(arguments, "baseline")),
            arguments);
        case LearningPlanRevisionToolContracts.OPERATION_FIND_PROBLEMS -> findProblems(
            revision,
            baselineResolver.resolve(revision, trusted.userId(), textOrNull(arguments, "baseline")),
            arguments);
        default -> failure("INVALID_OPERATION", "不支持的查询 operation。");
      };
    } catch (BaselineResolutionException exception) {
      return failure(exception.code(), exception.getMessage());
    } catch (IllegalArgumentException exception) {
      return failure("INVALID_ARGUMENTS", exception.getMessage());
    } catch (RuntimeException exception) {
      return failure("QUERY_FAILED", "读取学习计划修订快照失败。");
    }
  }

  private JsonNode readBaselineOptions(LearningPlanDraftRevision revision, long userId) {
    ObjectNode output = success();
    output.put("currentBaseline", LearningPlanRevisionToolContracts.BASELINE_CURRENT_REVISION);
    ArrayNode options = output.putArray("baselines");
    for (BaselineOption option : baselineResolver.options(revision, userId)) {
      ObjectNode node = options.addObject();
      node.put("baseline", option.baseline());
      node.put("available", option.available());
      node.put("description", option.description());
    }
    return output;
  }

  private JsonNode readPhase(
      LearningPlanDraftRevision revision,
      ResolvedBaseline resolved,
      JsonNode arguments
  ) {
    LearningPlanRevisionBaseSnapshot snapshot = resolved.snapshot();
    String phaseRef = requiredText(arguments, "phaseRef");
    int phaseIndex = parsePhaseRef(phaseRef);
    if (phaseIndex < 0 || phaseIndex >= snapshot.basePlan().phases().size()) {
      return failure("PHASE_NOT_FOUND", "冻结计划中不存在阶段：" + phaseRef);
    }
    int limit = limit(arguments);
    int offset = readOffset(
        textOrNull(arguments, "cursor"), phaseRef, revision.id(), resolved.baseline());
    LearningPlanPhaseDraft phase = snapshot.basePlan().phases().get(phaseIndex);
    int end = Math.min(phase.problems().size(), offset + limit);
    if (offset > end) {
      return failure("INVALID_CURSOR", "cursor 已超出阶段题目范围。");
    }
    ObjectNode output = success();
    output.put("baseline", resolved.baseline());
    output.set("phase", phaseSummary(phase, phaseRef));
    ArrayNode problems = output.putArray("problems");
    for (int index = offset; index < end; index++) {
      problems.add(problemNode(
          phase.problems().get(index),
          snapshot.baseBrief().contentLocale(),
          phaseRef,
          index + 1,
          true));
    }
    if (end < phase.problems().size()) {
      output.put("nextCursor", readCursor(phaseRef, end, revision.id(), resolved.baseline()));
    } else {
      output.putNull("nextCursor");
    }
    return output;
  }

  private JsonNode findProblems(
      LearningPlanDraftRevision revision,
      ResolvedBaseline resolved,
      JsonNode arguments
  ) {
    LearningPlanRevisionBaseSnapshot snapshot = resolved.snapshot();
    Set<String> phaseRefs = stringSet(arguments.path("phaseRefs"));
    Set<String> slugs = stringSet(arguments.path("slugs"));
    String difficulty = textOrNull(arguments, "difficulty");
    String keyword = textOrNull(arguments, "keyword");
    int limit = limit(arguments);
    String filterKey = Integer.toHexString(Objects.hash(phaseRefs, slugs, difficulty, keyword));
    int offset = findOffset(
        textOrNull(arguments, "cursor"), filterKey, revision.id(), resolved.baseline());

    List<LocatedProblem> matches = new ArrayList<>();
    for (int phaseIndex = 0; phaseIndex < snapshot.basePlan().phases().size(); phaseIndex++) {
      String phaseRef = LearningPlanRevisionModelViewProjector.phaseRef(phaseIndex);
      if (!phaseRefs.isEmpty() && !phaseRefs.contains(phaseRef)) {
        continue;
      }
      LearningPlanPhaseDraft phase = snapshot.basePlan().phases().get(phaseIndex);
      for (int problemIndex = 0; problemIndex < phase.problems().size(); problemIndex++) {
        LearningPlanProblemDraft problem = phase.problems().get(problemIndex);
        if (!slugs.isEmpty() && !slugs.contains(problem.slug())) {
          continue;
        }
        if (difficulty != null && !difficulty.equalsIgnoreCase(problem.difficulty())) {
          continue;
        }
        if (!matchesKeyword(problem, snapshot.baseBrief().contentLocale(), keyword)) {
          continue;
        }
        matches.add(new LocatedProblem(phaseRef, problemIndex + 1, problem));
      }
    }
    int end = Math.min(matches.size(), offset + limit);
    if (offset > end) {
      return failure("INVALID_CURSOR", "cursor 已超出查询结果范围。");
    }
    ObjectNode output = success();
    output.put("baseline", resolved.baseline());
    output.put("matchCount", matches.size());
    ArrayNode problems = output.putArray("problems");
    for (int index = offset; index < end; index++) {
      LocatedProblem located = matches.get(index);
      problems.add(problemNode(
          located.problem(),
          snapshot.baseBrief().contentLocale(),
          located.phaseRef(),
          located.position(),
          true));
    }
    if (end < matches.size()) {
      output.put("nextCursor", findCursor(end, filterKey, revision.id(), resolved.baseline()));
    } else {
      output.putNull("nextCursor");
    }
    return output;
  }

  private ObjectNode phaseSummary(LearningPlanPhaseDraft phase, String phaseRef) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("phaseRef", phaseRef);
    putNullable(node, "title", phase.title());
    putNullable(node, "focus", phase.focus());
    node.put("problemCount", phase.problems().size());
    node.set("difficultyCounts", difficultyCounts(phase.problems()));
    return node;
  }

  private ObjectNode problemNode(
      LearningPlanProblemDraft problem,
      LearningPlanContentLocale locale,
      String phaseRef,
      int position,
      boolean includeReason
  ) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("phaseRef", phaseRef);
    node.put("position", position);
    node.put("slug", problem.slug());
    node.put("displayTitle", displayTitle(problem, locale));
    putNullable(node, "difficulty", problem.difficulty());
    if (includeReason) {
      putNullable(node, "reason", problem.reason());
    }
    return node;
  }

  private ObjectNode difficultyCounts(List<LearningPlanProblemDraft> problems) {
    int easy = 0;
    int medium = 0;
    int hard = 0;
    for (LearningPlanProblemDraft problem : problems) {
      switch (LearningPlanRevisionModelViewProjector.normalizeDifficulty(problem.difficulty())) {
        case "EASY" -> easy++;
        case "HARD" -> hard++;
        default -> medium++;
      }
    }
    ObjectNode counts = JsonNodeFactory.instance.objectNode();
    counts.put("easy", easy);
    counts.put("medium", medium);
    counts.put("hard", hard);
    return counts;
  }

  private boolean matchesKeyword(
      LearningPlanProblemDraft problem,
      LearningPlanContentLocale locale,
      String keyword
  ) {
    if (keyword == null) {
      return true;
    }
    String normalized = keyword.toLowerCase(Locale.ROOT);
    return contains(problem.slug(), normalized)
        || contains(displayTitle(problem, locale), normalized)
        || contains(problem.reason(), normalized);
  }

  private boolean contains(String value, String normalizedKeyword) {
    return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedKeyword);
  }

  private String displayTitle(LearningPlanProblemDraft problem, LearningPlanContentLocale locale) {
    String preferred = locale == LearningPlanContentLocale.EN_US ? problem.title() : problem.titleCn();
    if (preferred != null && !preferred.isBlank()) {
      return preferred;
    }
    String fallback = locale == LearningPlanContentLocale.EN_US ? problem.titleCn() : problem.title();
    return fallback == null || fallback.isBlank() ? problem.slug() : fallback;
  }

  private int parsePhaseRef(String phaseRef) {
    if (!phaseRef.startsWith("phase-")) {
      return -1;
    }
    try {
      return Integer.parseInt(phaseRef.substring("phase-".length())) - 1;
    } catch (NumberFormatException exception) {
      return -1;
    }
  }

  private int limit(JsonNode arguments) {
    JsonNode value = arguments.get("limit");
    if (value == null || value.isNull()) {
      return DEFAULT_LIMIT;
    }
    if (!value.isIntegralNumber() || value.intValue() < 1 || value.intValue() > MAX_LIMIT) {
      throw new IllegalArgumentException("limit 必须在 1 到 30 之间。");
    }
    return value.intValue();
  }

  private int readOffset(String cursor, String phaseRef, long revisionId, String baseline) {
    if (cursor == null) {
      return 0;
    }
    String prefix = "read:" + phaseRef + ":";
    String suffix = cursorScope(revisionId, baseline);
    if (!cursor.startsWith(prefix) || !cursor.endsWith(suffix)) {
      throw new IllegalArgumentException("cursor 与当前冻结阶段不匹配。");
    }
    return parseOffset(cursor.substring(prefix.length(), cursor.length() - suffix.length()));
  }

  private int findOffset(String cursor, String filterKey, long revisionId, String baseline) {
    if (cursor == null) {
      return 0;
    }
    String prefix = "find:" + filterKey + ":";
    String suffix = cursorScope(revisionId, baseline);
    if (!cursor.startsWith(prefix) || !cursor.endsWith(suffix)) {
      throw new IllegalArgumentException("cursor 与当前冻结筛选条件不匹配。");
    }
    return parseOffset(cursor.substring(prefix.length(), cursor.length() - suffix.length()));
  }

  private int parseOffset(String value) {
    try {
      int offset = Integer.parseInt(value);
      if (offset < 0) {
        throw new NumberFormatException("negative");
      }
      return offset;
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("cursor 无效。");
    }
  }

  private String readCursor(String phaseRef, int offset, long revisionId, String baseline) {
    return "read:" + phaseRef + ":" + offset + cursorScope(revisionId, baseline);
  }

  private String findCursor(int offset, String filterKey, long revisionId, String baseline) {
    return "find:" + filterKey + ":" + offset + cursorScope(revisionId, baseline);
  }

  private String cursorScope(long revisionId, String baseline) {
    return ":r" + revisionId + ":b" + baseline;
  }

  private Set<String> stringSet(JsonNode node) {
    if (!node.isArray()) {
      throw new IllegalArgumentException("phaseRefs 和 slugs 必须是字符串数组。");
    }
    Set<String> result = new HashSet<>();
    for (JsonNode item : node) {
      if (!item.isTextual() || item.textValue().isBlank()) {
        throw new IllegalArgumentException("phaseRefs 和 slugs 只能包含非空字符串。");
      }
      result.add(item.textValue().trim());
    }
    return Set.copyOf(result);
  }

  private String requiredText(JsonNode arguments, String field) {
    String value = textOrNull(arguments, field);
    if (value == null) {
      throw new IllegalArgumentException(field + " 不能为空。");
    }
    return value;
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

  private ObjectNode success() {
    ObjectNode result = JsonNodeFactory.instance.objectNode();
    result.put("status", "OK");
    result.put("snapshotState", "FROZEN");
    return result;
  }

  private ObjectNode failure(String code, String message) {
    ObjectNode result = JsonNodeFactory.instance.objectNode();
    result.put("status", "FAILED");
    result.put("failureCode", code);
    result.put("message", message);
    return result;
  }

  private void putNullable(ObjectNode node, String field, String value) {
    if (value == null) {
      node.putNull(field);
    } else {
      node.put(field, value);
    }
  }

  private static JsonNode inputSchema() {
    ObjectNode root = object();
    root.put("additionalProperties", false);
    ObjectNode properties = root.putObject("properties");
    properties.set("operation", enumString(
        LearningPlanRevisionToolContracts.OPERATION_READ_BASELINE_OPTIONS,
        LearningPlanRevisionToolContracts.OPERATION_READ_PHASE,
        LearningPlanRevisionToolContracts.OPERATION_FIND_PROBLEMS));
    properties.set("baseline", nullableEnum(
        LearningPlanRevisionToolContracts.BASELINE_CURRENT_REVISION,
        LearningPlanRevisionToolContracts.BASELINE_ORIGINAL_DRAFT,
        LearningPlanRevisionToolContracts.BASELINE_PREVIOUS_REVISION));
    properties.set("phaseRef", nullableString());
    properties.set("phaseRefs", stringArray());
    properties.set("slugs", stringArray());
    ObjectNode difficulty = nullableString();
    difficulty.putArray("enum").add("EASY").add("MEDIUM").add("HARD").addNull();
    properties.set("difficulty", difficulty);
    properties.set("keyword", nullableString());
    properties.set("cursor", nullableString());
    ObjectNode limit = JsonNodeFactory.instance.objectNode();
    limit.putArray("type").add("integer").add("null");
    limit.put("minimum", 1);
    limit.put("maximum", MAX_LIMIT);
    properties.set("limit", limit);
    requireAll(root, properties);
    return root;
  }

  private static ObjectNode object() {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("type", "object");
    return node;
  }

  private static ObjectNode nullableString() {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.putArray("type").add("string").add("null");
    return node;
  }

  private static ObjectNode nullableEnum(String... values) {
    ObjectNode node = nullableString();
    ArrayNode valuesNode = node.putArray("enum");
    for (String value : values) {
      valuesNode.add(value);
    }
    valuesNode.addNull();
    return node;
  }

  private static ObjectNode stringArray() {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("type", "array");
    ObjectNode item = JsonNodeFactory.instance.objectNode();
    item.put("type", "string");
    node.set("items", item);
    return node;
  }

  private static ObjectNode enumString(String... values) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("type", "string");
    ArrayNode valuesNode = node.putArray("enum");
    for (String value : values) {
      valuesNode.add(value);
    }
    return node;
  }

  private static void requireAll(ObjectNode root, ObjectNode properties) {
    ArrayNode required = root.putArray("required");
    properties.fieldNames().forEachRemaining(required::add);
  }

  private record LocatedProblem(String phaseRef, int position, LearningPlanProblemDraft problem) {
  }
}
