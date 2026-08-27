package org.congcong.algomentor.mentor.application.profile.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Set;
import org.congcong.algomentor.agent.core.AgentExecutionContext;
import org.congcong.algomentor.agent.core.AgentTool;
import org.congcong.algomentor.llm.core.tool.LlmToolSpec;
import org.congcong.algomentor.mentor.application.profile.recall.LearnerMemoryRecallSnapshot;
import org.congcong.algomentor.mentor.application.profile.observability.LearnerMemoryMetrics;

/** 按 snapshot 固定投影顺序读取一个完整记忆主题。 */
public final class ReadLearnerMemorySectionAgentTool implements AgentTool {

  private static final String FIELD_NEXT_AFTER_STATEMENT_REF = "nextAfterStatementRef";
  private static final Set<String> FIELDS = Set.of(
      LearnerMemoryRecallToolContracts.ARGUMENT_SECTION_REF,
      LearnerMemoryRecallToolContracts.ARGUMENT_AFTER_STATEMENT_REF,
      LearnerMemoryRecallToolContracts.ARGUMENT_LIMIT);
  private static final LlmToolSpec SPEC = new LlmToolSpec(
      LearnerMemoryRecallToolContracts.READ_LEARNER_MEMORY_SECTION,
      "Read one bounded learner-memory section from this chat run. Continue only with nextAfterStatementRef returned by this tool.",
      schema(),
      true);

  private final LearnerMemoryRunScopeRegistry scopeRegistry;
  private final LearnerMemoryRecallToolMetrics observability;

  public ReadLearnerMemorySectionAgentTool(LearnerMemoryRunScopeRegistry scopeRegistry) {
    this(scopeRegistry, LearnerMemoryMetrics.NOOP);
  }

  public ReadLearnerMemorySectionAgentTool(
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
    String sectionRef = LearnerMemoryRecallToolSupport.requiredText(arguments,
        LearnerMemoryRecallToolContracts.ARGUMENT_SECTION_REF);
    String afterStatementRef = LearnerMemoryRecallToolSupport.optionalText(arguments,
        LearnerMemoryRecallToolContracts.ARGUMENT_AFTER_STATEMENT_REF);
    if (limit < 1 || sectionRef == null || afterStatementRef == null) {
      return observability.record(LearnerMemoryRecallToolContracts.READ_LEARNER_MEMORY_SECTION,
          LearnerMemoryRecallToolSupport.failure(
          LearnerMemoryRecallToolContracts.TYPE_SECTION,
          LearnerMemoryRecallToolContracts.STATUS_FAILED,
          LearnerMemoryRecallToolContracts.FAILURE_INVALID_ARGUMENTS));
    }
    LearnerMemoryRunScopeRegistry.RecallScopeUse use = scopeRegistry.reserveRecallTool(
        LearnerMemoryRecallToolSupport.scopeRef(context));
    if (!use.granted()) {
      return observability.record(LearnerMemoryRecallToolContracts.READ_LEARNER_MEMORY_SECTION,
          LearnerMemoryRecallToolSupport.failureForScope(LearnerMemoryRecallToolContracts.TYPE_SECTION, use.status()));
    }
    ObjectNode result = null;
    try {
      LearnerMemoryRecallSnapshot.Section section = LearnerMemoryRecallToolSupport.section(use.snapshot(), sectionRef);
      if (section == null) {
        result = LearnerMemoryRecallToolSupport.failure(
            LearnerMemoryRecallToolContracts.TYPE_SECTION,
            LearnerMemoryRecallToolContracts.STATUS_FAILED,
            LearnerMemoryRecallToolContracts.FAILURE_NOT_FOUND_OR_NOT_READABLE);
      } else {
        result = page(use, section, afterStatementRef, limit);
      }
    } catch (RuntimeException exception) {
      result = LearnerMemoryRecallToolSupport.failure(
          LearnerMemoryRecallToolContracts.TYPE_SECTION,
          LearnerMemoryRecallToolContracts.STATUS_FAILED,
          LearnerMemoryRecallToolContracts.FAILURE_INTERNAL);
    } finally {
      use.complete(result == null ? 0 : result.toString().length());
    }
    return observability.record(LearnerMemoryRecallToolContracts.READ_LEARNER_MEMORY_SECTION, result);
  }

  private ObjectNode page(
      LearnerMemoryRunScopeRegistry.RecallScopeUse use,
      LearnerMemoryRecallSnapshot.Section section,
      String afterStatementRef,
      int limit
  ) {
    int start = 0;
    if (!afterStatementRef.isBlank()) {
      start = indexAfter(section.statements(), afterStatementRef);
      if (start < 0) {
        return LearnerMemoryRecallToolSupport.failure(
            LearnerMemoryRecallToolContracts.TYPE_SECTION,
            LearnerMemoryRecallToolContracts.STATUS_FAILED,
            LearnerMemoryRecallToolContracts.FAILURE_NOT_FOUND_OR_NOT_READABLE);
      }
    }
    ObjectNode result = LearnerMemoryRecallToolSupport.success(LearnerMemoryRecallToolContracts.TYPE_SECTION);
    result.put(LearnerMemoryRecallToolContracts.FIELD_SECTION_REF, section.sectionRef());
    result.put(LearnerMemoryRecallToolContracts.FIELD_SECTION_TITLE, section.title());
    ArrayNode items = LearnerMemoryRecallToolSupport.items(result);
    int next = start;
    while (next < section.statements().size() && items.size() < limit) {
      LearnerMemoryRecallSnapshot.Statement statement = section.statements().get(next);
      ObjectNode item = items.addObject();
      LearnerMemoryRecallToolSupport.writeStatement(item, section, statement);
      if (!LearnerMemoryRecallToolSupport.fits(result, use.maxVisibleChars())) {
        items.remove(items.size() - 1);
        break;
      }
      next++;
    }
    if (next < section.statements().size() && items.size() > 0) {
      result.put(FIELD_NEXT_AFTER_STATEMENT_REF, items.get(items.size() - 1)
          .path(LearnerMemoryRecallToolContracts.FIELD_STATEMENT_REF).asText());
      if (!LearnerMemoryRecallToolSupport.fits(result, use.maxVisibleChars())) {
        items.remove(items.size() - 1);
        if (items.size() > 0) {
          result.put(FIELD_NEXT_AFTER_STATEMENT_REF, items.get(items.size() - 1)
              .path(LearnerMemoryRecallToolContracts.FIELD_STATEMENT_REF).asText());
        } else {
          result.remove(FIELD_NEXT_AFTER_STATEMENT_REF);
        }
      }
    }
    return result;
  }

  private int indexAfter(List<LearnerMemoryRecallSnapshot.Statement> statements, String statementRef) {
    for (int index = 0; index < statements.size(); index++) {
      if (statementRef.equals(statements.get(index).statementRef())) {
        return index + 1;
      }
    }
    return -1;
  }

  private static ObjectNode schema() {
    ObjectNode schema = JsonNodeFactory.instance.objectNode();
    schema.put("type", "object");
    schema.put("additionalProperties", false);
    ObjectNode properties = schema.putObject("properties");
    properties.putObject(LearnerMemoryRecallToolContracts.ARGUMENT_SECTION_REF).put("type", "string");
    properties.putObject(LearnerMemoryRecallToolContracts.ARGUMENT_AFTER_STATEMENT_REF)
        .put("type", "string")
        .put("description", "Empty string for the first page; use nextAfterStatementRef for later pages.");
    properties.putObject(LearnerMemoryRecallToolContracts.ARGUMENT_LIMIT).put("type", "integer").put("minimum", 1)
        .put("maximum", LearnerMemoryRecallToolContracts.MAX_ITEMS);
    schema.putArray("required")
        .add(LearnerMemoryRecallToolContracts.ARGUMENT_SECTION_REF)
        .add(LearnerMemoryRecallToolContracts.ARGUMENT_AFTER_STATEMENT_REF)
        .add(LearnerMemoryRecallToolContracts.ARGUMENT_LIMIT);
    return schema;
  }
}
