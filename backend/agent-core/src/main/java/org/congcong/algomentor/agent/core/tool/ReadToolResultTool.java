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
import org.congcong.algomentor.agent.core.toolresult.StoredToolResult;
import org.congcong.algomentor.agent.core.toolresult.ToolResultStore;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.tool.LlmToolSpec;

public final class ReadToolResultTool implements AgentTool {

  public static final String NAME = "read_tool_result";

  private final ToolResultStore resultStore;
  private final ToolResultCompactionPolicy policy;

  public ReadToolResultTool(ToolResultStore resultStore, ToolResultCompactionPolicy policy) {
    this.resultStore = Objects.requireNonNull(resultStore, "resultStore must not be null");
    this.policy = policy == null ? ToolResultCompactionPolicy.defaults() : policy;
  }

  @Override
  public LlmToolSpec spec() {
    ObjectNode schema = JsonNodeFactory.instance.objectNode();
    schema.put("type", "object");
    ObjectNode properties = schema.putObject("properties");
    properties.putObject("resultRef").put("type", "string");
    properties.putObject("offset").put("type", "integer").put("minimum", 0);
    properties.putObject("limit").put("type", "integer").put("minimum", 1);
    properties.putObject("lineStart").put("type", "integer").put("minimum", 1);
    properties.putObject("lineEnd").put("type", "integer").put("minimum", 1);
    schema.putArray("required").add("resultRef");
    schema.put("additionalProperties", false);
    return new LlmToolSpec(NAME, "Read a bounded range from a large prior tool result.", schema, true);
  }

  @Override
  public JsonNode execute(JsonNode arguments, AgentExecutionContext context) {
    String resultRef = requiredText(arguments, "resultRef");
    StoredToolResult stored = resultStore.findByResultRef(loopContext(context), resultRef)
        .orElseThrow(() -> new IllegalArgumentException("Tool result reference was not found or is not readable"));
    if (has(arguments, "lineStart") || has(arguments, "lineEnd")) {
      return readLineRange(stored, intValue(arguments, "lineStart", 1), intValue(arguments, "lineEnd", 1));
    }
    return readOffsetRange(stored, intValue(arguments, "offset", 0), intValue(arguments, "limit", policy.rangeReadMaxChars()));
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
    range.put("offset", start);
    range.put("limit", end - start);
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
    range.put("lineStart", fromLine);
    range.put("lineEnd", Math.min(toLine, lines.length));
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
    output.put("type", "tool_result_range");
    output.put("resultRef", stored.resultRef());
    output.put("contentType", stored.contentType());
    output.set("range", range);
    output.put("content", content);
    output.put("charCount", content.length());
    output.put("hasMoreBefore", hasMoreBefore);
    output.put("hasMoreAfter", hasMoreAfter);
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
}
