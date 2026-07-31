package org.congcong.algomentor.mentor.application.profile.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.congcong.algomentor.agent.core.AgentExecutionContext;
import org.congcong.algomentor.agent.core.AgentTool;
import org.congcong.algomentor.llm.core.tool.LlmToolSpec;
import org.congcong.algomentor.mentor.application.profile.recall.LearnerMemoryRecallSnapshot;
import org.congcong.algomentor.mentor.application.profile.observability.LearnerMemoryMetrics;

/** 在当前 run-local snapshot 中按规范化文本、主题和 tag 受限搜索。 */
public final class SearchLearnerMemoryAgentTool implements AgentTool {

  private static final Set<String> FIELDS = Set.of(
      LearnerMemoryRecallToolContracts.ARGUMENT_QUERY,
      LearnerMemoryRecallToolContracts.ARGUMENT_SECTION_REF,
      LearnerMemoryRecallToolContracts.ARGUMENT_TAG_VALUES,
      LearnerMemoryRecallToolContracts.ARGUMENT_LIMIT,
      LearnerMemoryRecallToolContracts.ARGUMENT_CURSOR);
  private static final LlmToolSpec SPEC = new LlmToolSpec(
      LearnerMemoryRecallToolContracts.SEARCH_LEARNER_MEMORY,
      "Search only the learner-memory snapshot for this chat run. A missing match does not mean the learner has no experience.",
      schema(),
      true);

  private final LearnerMemoryRunScopeRegistry scopeRegistry;
  private final LearnerMemoryRecallToolMetrics observability;

  public SearchLearnerMemoryAgentTool(LearnerMemoryRunScopeRegistry scopeRegistry) {
    this(scopeRegistry, LearnerMemoryMetrics.NOOP);
  }

  public SearchLearnerMemoryAgentTool(
      LearnerMemoryRunScopeRegistry scopeRegistry,
      LearnerMemoryMetrics metrics) {
    this.scopeRegistry = scopeRegistry;
    this.observability = new LearnerMemoryRecallToolMetrics(metrics);
  }

  @Override
  public LlmToolSpec spec() {
    return SPEC;
  }

  @Override
  public JsonNode execute(JsonNode arguments, AgentExecutionContext context) {
    int limit = LearnerMemoryRecallToolSupport.requiredLimit(arguments, FIELDS);
    String query = LearnerMemoryRecallToolSupport.requiredText(arguments, LearnerMemoryRecallToolContracts.ARGUMENT_QUERY);
    String sectionRef = LearnerMemoryRecallToolSupport.optionalText(arguments,
        LearnerMemoryRecallToolContracts.ARGUMENT_SECTION_REF);
    String cursor = LearnerMemoryRecallToolSupport.optionalText(arguments, LearnerMemoryRecallToolContracts.ARGUMENT_CURSOR);
    Set<Long> tagValues = LearnerMemoryRecallToolSupport.optionalPositiveLongs(arguments,
        LearnerMemoryRecallToolContracts.ARGUMENT_TAG_VALUES);
    String normalizedQuery = LearnerMemoryRecallToolSupport.normalizedText(query);
    if (limit < 1 || sectionRef == null || cursor == null || tagValues == null || normalizedQuery.isBlank()) {
      return observability.record(LearnerMemoryRecallToolContracts.SEARCH_LEARNER_MEMORY,
          LearnerMemoryRecallToolSupport.failure(
          LearnerMemoryRecallToolContracts.TYPE_SEARCH,
          LearnerMemoryRecallToolContracts.STATUS_FAILED,
          LearnerMemoryRecallToolContracts.FAILURE_INVALID_ARGUMENTS));
    }

    LearnerMemoryRunScopeRegistry.RecallScopeUse use = scopeRegistry.reserveRecallTool(
        LearnerMemoryRecallToolSupport.scopeRef(context));
    if (!use.granted()) {
      return observability.record(LearnerMemoryRecallToolContracts.SEARCH_LEARNER_MEMORY,
          LearnerMemoryRecallToolSupport.failureForScope(LearnerMemoryRecallToolContracts.TYPE_SEARCH, use.status()));
    }
    ObjectNode result = null;
    try {
      LearnerMemoryRecallSnapshot.Section scopedSection = sectionRef.isBlank()
          ? null : LearnerMemoryRecallToolSupport.section(use.snapshot(), sectionRef);
      if (!sectionRef.isBlank() && scopedSection == null) {
        result = LearnerMemoryRecallToolSupport.failure(
            LearnerMemoryRecallToolContracts.TYPE_SEARCH,
            LearnerMemoryRecallToolContracts.STATUS_FAILED,
            LearnerMemoryRecallToolContracts.FAILURE_NOT_FOUND_OR_NOT_READABLE);
      } else {
        String cursorSubject = cursorSubject(normalizedQuery, sectionRef, tagValues);
        java.util.OptionalInt start = LearnerMemoryRecallToolSupport.cursorOffset(
            use, cursor, LearnerMemoryRecallToolContracts.CURSOR_SEARCH, cursorSubject);
        if (start.isEmpty()) {
          result = LearnerMemoryRecallToolSupport.failure(
              LearnerMemoryRecallToolContracts.TYPE_SEARCH,
              LearnerMemoryRecallToolContracts.STATUS_FAILED,
              LearnerMemoryRecallToolContracts.FAILURE_NOT_FOUND_OR_NOT_READABLE);
        } else {
          result = page(use, findMatches(use.snapshot(), scopedSection, normalizedQuery, tagValues), start.getAsInt(), limit,
              cursorSubject);
        }
      }
    } catch (RuntimeException exception) {
      result = LearnerMemoryRecallToolSupport.failure(
          LearnerMemoryRecallToolContracts.TYPE_SEARCH,
          LearnerMemoryRecallToolContracts.STATUS_FAILED,
          LearnerMemoryRecallToolContracts.FAILURE_INTERNAL);
    } finally {
      use.complete(result == null ? 0 : result.toString().length());
    }
    return observability.record(LearnerMemoryRecallToolContracts.SEARCH_LEARNER_MEMORY, result);
  }

  private ObjectNode page(
      LearnerMemoryRunScopeRegistry.RecallScopeUse use,
      List<StatementLocation> matches,
      int start,
      int limit,
      String cursorSubject
  ) {
    if (start < 0 || start > matches.size()) {
      return LearnerMemoryRecallToolSupport.failure(
          LearnerMemoryRecallToolContracts.TYPE_SEARCH,
          LearnerMemoryRecallToolContracts.STATUS_FAILED,
          LearnerMemoryRecallToolContracts.FAILURE_NOT_FOUND_OR_NOT_READABLE);
    }
    ObjectNode result = LearnerMemoryRecallToolSupport.success(LearnerMemoryRecallToolContracts.TYPE_SEARCH);
    ArrayNode items = LearnerMemoryRecallToolSupport.items(result);
    int next = start;
    while (next < matches.size() && items.size() < limit) {
      StatementLocation location = matches.get(next);
      ObjectNode item = items.addObject();
      LearnerMemoryRecallToolSupport.writeStatement(item, location.section(), location.statement());
      if (!LearnerMemoryRecallToolSupport.fits(result, use.maxVisibleChars())) {
        items.remove(items.size() - 1);
        break;
      }
      next++;
    }
    if (next < matches.size()) {
      String nextCursor = use.createCursor(LearnerMemoryRecallToolContracts.CURSOR_SEARCH, cursorSubject, next);
      result.put(LearnerMemoryRecallToolContracts.FIELD_NEXT_CURSOR, nextCursor);
      if (!LearnerMemoryRecallToolSupport.fits(result, use.maxVisibleChars()) && items.size() > 0) {
        items.remove(items.size() - 1);
        next--;
        result.put(LearnerMemoryRecallToolContracts.FIELD_NEXT_CURSOR,
            use.createCursor(LearnerMemoryRecallToolContracts.CURSOR_SEARCH, cursorSubject, next));
      }
    }
    if (matches.isEmpty()) {
      result.put(LearnerMemoryRecallToolContracts.FIELD_MESSAGE, LearnerMemoryRecallToolContracts.MESSAGE_EMPTY_SEARCH);
    }
    return result;
  }

  private List<StatementLocation> findMatches(
      LearnerMemoryRecallSnapshot snapshot,
      LearnerMemoryRecallSnapshot.Section scopedSection,
      String query,
      Set<Long> tagValues
  ) {
    List<StatementLocation> matches = new ArrayList<>();
    List<LearnerMemoryRecallSnapshot.Section> sections = scopedSection == null ? snapshot.sections() : List.of(scopedSection);
    for (LearnerMemoryRecallSnapshot.Section section : sections) {
      for (LearnerMemoryRecallSnapshot.Statement statement : section.statements()) {
        Long tagId = statement.claim().scope().tagId();
        if ((!tagValues.isEmpty() && (tagId == null || !tagValues.contains(tagId)))
            || !LearnerMemoryRecallToolSupport.normalizedText(statement.claim().claimText()).contains(query)) {
          continue;
        }
        matches.add(new StatementLocation(section, statement));
      }
    }
    return List.copyOf(matches);
  }

  private String cursorSubject(String query, String sectionRef, Set<Long> tagValues) {
    return query + "|" + sectionRef + "|" + tagValues.stream().sorted().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
  }

  private static ObjectNode schema() {
    ObjectNode schema = JsonNodeFactory.instance.objectNode();
    schema.put("type", "object");
    schema.put("additionalProperties", false);
    ObjectNode properties = schema.putObject("properties");
    properties.putObject(LearnerMemoryRecallToolContracts.ARGUMENT_QUERY).put("type", "string");
    properties.putObject(LearnerMemoryRecallToolContracts.ARGUMENT_SECTION_REF).put("type", "string");
    ObjectNode tags = properties.putObject(LearnerMemoryRecallToolContracts.ARGUMENT_TAG_VALUES);
    tags.put("type", "array");
    tags.putObject("items").put("type", "integer");
    properties.putObject(LearnerMemoryRecallToolContracts.ARGUMENT_LIMIT).put("type", "integer").put("minimum", 1)
        .put("maximum", LearnerMemoryRecallToolContracts.MAX_ITEMS);
    properties.putObject(LearnerMemoryRecallToolContracts.ARGUMENT_CURSOR).put("type", "string");
    schema.putArray("required")
        .add(LearnerMemoryRecallToolContracts.ARGUMENT_QUERY)
        .add(LearnerMemoryRecallToolContracts.ARGUMENT_LIMIT);
    return schema;
  }

  private record StatementLocation(
      LearnerMemoryRecallSnapshot.Section section,
      LearnerMemoryRecallSnapshot.Statement statement
  ) {
  }
}
