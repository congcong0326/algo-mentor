package org.congcong.algomentor.agent.core.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Objects;
import org.congcong.algomentor.agent.core.AgentExecutionContext;
import org.congcong.algomentor.agent.core.AgentLoopContext;
import org.congcong.algomentor.agent.core.AgentRequest;
import org.congcong.algomentor.agent.core.AgentTool;
import org.congcong.algomentor.agent.core.compaction.ToolResultCompactionPolicy;
import org.congcong.algomentor.agent.core.runtime.model.AgentToolResultJsonKeys;
import org.congcong.algomentor.agent.core.runtime.model.AgentToolResultTypes;
import org.congcong.algomentor.agent.core.toolresult.StoredToolResult;
import org.congcong.algomentor.agent.core.toolresult.ToolResultStore;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.tool.LlmToolSpec;

public final class ReadToolResultTool implements AgentTool {

  public static final String NAME = "read_tool_result";

  private static final String PROPERTIES = "properties";
  private static final String REQUIRED = "required";
  private static final String ADDITIONAL_PROPERTIES = "additionalProperties";
  private static final String OBJECT_TYPE = "object";
  private static final String STRING_TYPE = "string";
  private static final String INTEGER_TYPE = "integer";
  private static final String NULL_TYPE = "null";
  private static final String MINIMUM = "minimum";

  private final ToolResultStore resultStore;
  private final ToolResultCompactionPolicy policy;

  public ReadToolResultTool(ToolResultStore resultStore, ToolResultCompactionPolicy policy) {
    this.resultStore = Objects.requireNonNull(resultStore, "resultStore must not be null");
    this.policy = policy == null ? ToolResultCompactionPolicy.defaults() : policy;
  }

  @Override
  public LlmToolSpec spec() {
    ObjectNode schema = JsonNodeFactory.instance.objectNode();
    schema.put(AgentToolResultJsonKeys.TYPE, OBJECT_TYPE);
    ObjectNode properties = schema.putObject(PROPERTIES);
    properties.putObject(AgentToolResultJsonKeys.RESULT_REF).put(AgentToolResultJsonKeys.TYPE, STRING_TYPE);
    properties.set(AgentToolResultJsonKeys.OFFSET, nullableIntegerProperty(0));
    properties.set(AgentToolResultJsonKeys.LIMIT, nullableIntegerProperty(1));
    properties.set(AgentToolResultJsonKeys.LINE_START, nullableIntegerProperty(1));
    properties.set(AgentToolResultJsonKeys.LINE_END, nullableIntegerProperty(1));
    schema.putArray(REQUIRED)
        .add(AgentToolResultJsonKeys.RESULT_REF)
        .add(AgentToolResultJsonKeys.OFFSET)
        .add(AgentToolResultJsonKeys.LIMIT)
        .add(AgentToolResultJsonKeys.LINE_START)
        .add(AgentToolResultJsonKeys.LINE_END);
    schema.put(ADDITIONAL_PROPERTIES, false);
    return new LlmToolSpec(NAME, "Read a bounded range from a large prior tool result.", schema, true);
  }

  @Override
  public JsonNode execute(JsonNode arguments, AgentExecutionContext context) {
    String resultRef = requiredText(arguments, AgentToolResultJsonKeys.RESULT_REF);
    StoredToolResult stored = resultStore.findByResultRef(loopContext(context), resultRef)
        .orElseThrow(() -> new IllegalArgumentException("Tool result reference was not found or is not readable"));
    if (has(arguments, AgentToolResultJsonKeys.LINE_START) || has(arguments, AgentToolResultJsonKeys.LINE_END)) {
      return readLineRange(
          stored,
          intValue(arguments, AgentToolResultJsonKeys.LINE_START, 1),
          intValue(arguments, AgentToolResultJsonKeys.LINE_END, 1));
    }
    return readOffsetRange(
        stored,
        intValue(arguments, AgentToolResultJsonKeys.OFFSET, 0),
        intValue(arguments, AgentToolResultJsonKeys.LIMIT, policy.rangeReadMaxChars()));
  }

  private AgentLoopContext loopContext(AgentExecutionContext context) {
    if (context == null) {
      return null;
    }
    AgentRequest request = new AgentRequest(
        context.runId(),
        context.runId(),
        java.util.List.of(LlmMessage.user("read tool result")),
        context.requestMetadata());
    return new AgentLoopContext(context.runId(), request, context.stepIndex(), context.requestMetadata());
  }

  private JsonNode readOffsetRange(StoredToolResult stored, int offset, int requestedLimit) {
    int start = Math.max(0, Math.min(offset, stored.contentText().length()));
    int limit = Math.max(1, Math.min(requestedLimit, policy.rangeReadMaxChars()));
    int end = Math.min(stored.contentText().length(), start + limit);
    ObjectNode range = JsonNodeFactory.instance.objectNode();
    range.put(AgentToolResultJsonKeys.OFFSET, start);
    range.put(AgentToolResultJsonKeys.LIMIT, end - start);
    return output(stored, range, stored.contentText().substring(start, end), start > 0, end < stored.contentText().length());
  }

  private JsonNode readLineRange(StoredToolResult stored, int lineStart, int lineEnd) {
    int fromLine = Math.max(1, lineStart);
    int toLine = Math.max(fromLine, lineEnd);
    String[] lines = stored.contentText().split("\\R", -1);
    StringBuilder content = new StringBuilder();
    for (int line = fromLine; line <= toLine && line <= lines.length; line++) {
      if (!content.isEmpty()) {
        content.append('\n');
      }
      content.append(lines[line - 1]);
      if (content.length() >= policy.rangeReadMaxChars()) {
        content.setLength(policy.rangeReadMaxChars());
        break;
      }
    }
    ObjectNode range = JsonNodeFactory.instance.objectNode();
    range.put(AgentToolResultJsonKeys.LINE_START, fromLine);
    range.put(AgentToolResultJsonKeys.LINE_END, Math.min(toLine, lines.length));
    return output(stored, range, content.toString(), fromLine > 1, toLine < lines.length);
  }

  private JsonNode output(
      StoredToolResult stored,
      ObjectNode range,
      String content,
      boolean hasMoreBefore,
      boolean hasMoreAfter
  ) {
    ObjectNode output = JsonNodeFactory.instance.objectNode();
    output.put(AgentToolResultJsonKeys.TYPE, AgentToolResultTypes.RANGE);
    output.put(AgentToolResultJsonKeys.RESULT_REF, stored.resultRef());
    output.put(AgentToolResultJsonKeys.CONTENT_TYPE, stored.contentType());
    output.set(AgentToolResultJsonKeys.RANGE, range);
    output.put(AgentToolResultJsonKeys.CONTENT, content);
    output.put(AgentToolResultJsonKeys.CHAR_COUNT, content.length());
    output.put(AgentToolResultJsonKeys.HAS_MORE_BEFORE, hasMoreBefore);
    output.put(AgentToolResultJsonKeys.HAS_MORE_AFTER, hasMoreAfter);
    return output;
  }

  private String requiredText(JsonNode arguments, String fieldName) {
    JsonNode value = arguments == null ? null : arguments.get(fieldName);
    if (value == null || !value.isTextual() || value.asText().isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
    return value.asText();
  }

  private boolean has(JsonNode arguments, String fieldName) {
    return arguments != null && arguments.has(fieldName) && !arguments.get(fieldName).isNull();
  }

  private int intValue(JsonNode arguments, String fieldName, int defaultValue) {
    JsonNode value = arguments == null ? null : arguments.get(fieldName);
    return value == null || !value.canConvertToInt() ? defaultValue : value.asInt();
  }

  private ObjectNode nullableIntegerProperty(int minimum) {
    ObjectNode property = JsonNodeFactory.instance.objectNode().put(MINIMUM, minimum);
    property.putArray(AgentToolResultJsonKeys.TYPE).add(INTEGER_TYPE).add(NULL_TYPE);
    return property;
  }
}
