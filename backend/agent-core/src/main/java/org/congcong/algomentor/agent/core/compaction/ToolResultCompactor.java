package org.congcong.algomentor.agent.core.compaction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import org.congcong.algomentor.agent.core.AgentLoopContext;
import org.congcong.algomentor.agent.core.toolresult.StoredToolResult;
import org.congcong.algomentor.agent.core.toolresult.ToolResultStore;
import org.congcong.algomentor.llm.core.tool.LlmToolCall;

public final class ToolResultCompactor {

  public static final String CONTENT_TYPE_JSON = "application/json";

  private final ObjectMapper objectMapper;
  private final ToolResultCompactionPolicy policy;
  private final ToolResultStore resultStore;

  public ToolResultCompactor(ObjectMapper objectMapper, ToolResultCompactionPolicy policy, ToolResultStore resultStore) {
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    this.policy = policy == null ? ToolResultCompactionPolicy.defaults() : policy;
    this.resultStore = resultStore;
  }

  public ToolResultCompaction compactForModel(
      AgentLoopContext context,
      int stepIndex,
      LlmToolCall toolCall,
      JsonNode redactedResult
  ) {
    Objects.requireNonNull(toolCall, "toolCall must not be null");
    Objects.requireNonNull(redactedResult, "redactedResult must not be null");
    String serialized = canonicalJson(redactedResult);
    int charCount = serialized.length();
    int lineCount = lineCount(serialized);
    if (charCount <= policy.inlineMaxChars()) {
      return new ToolResultCompaction(
          redactedResult,
          new ToolResultCompactionMetadata(
              "inline",
              null,
              null,
              charCount,
              charCount,
              lineCount,
              sha256(serialized),
              CONTENT_TYPE_JSON,
              false,
              ToolResultCompactionPolicy.POLICY_VERSION));
    }

    StoredToolResult stored = saveLargeResult(context, stepIndex, toolCall, redactedResult, serialized);
    int previewMax = Math.min(policy.previewMaxChars(), stored.contentText().length());
    String preview = stored.contentText().substring(0, previewMax);
    ObjectNode visible = objectMapper.createObjectNode();
    visible.put("type", "tool_result_preview");
    visible.put("resultRef", stored.resultRef());
    visible.put("toolCallId", toolCall.id());
    visible.put("toolName", toolCall.name());
    visible.put("contentType", stored.contentType());
    visible.put("charCount", stored.charCount());
    visible.put("lineCount", stored.lineCount());
    visible.put("preview", preview);
    visible.put("truncated", true);
    addShape(visible, redactedResult);
    visible.put(
        "readHint",
        "Use read_tool_result with resultRef and offset/limit or line range if more detail is needed.");
    return new ToolResultCompaction(
        visible,
        new ToolResultCompactionMetadata(
            "blob",
            stored.resultRef(),
            stored.blobId(),
            preview.length(),
            stored.charCount(),
            stored.lineCount(),
            stored.sha256(),
            stored.contentType(),
            true,
            ToolResultCompactionPolicy.POLICY_VERSION));
  }

  public JsonNode compactedPlaceholder(String toolCallId, String toolName, String resultRef) {
    ObjectNode node = objectMapper.createObjectNode();
    node.put("type", "tool_result_compacted");
    if (resultRef != null && !resultRef.isBlank()) {
      node.put("resultRef", resultRef);
    }
    if (toolCallId != null && !toolCallId.isBlank()) {
      node.put("toolCallId", toolCallId);
    }
    if (toolName != null && !toolName.isBlank()) {
      node.put("toolName", toolName);
    }
    node.put("message", "Earlier tool result compacted. Re-read a range if needed.");
    node.put("truncated", true);
    return node;
  }

  public ToolResultCompactionPolicy policy() {
    return policy;
  }

  private StoredToolResult saveLargeResult(
      AgentLoopContext context,
      int stepIndex,
      LlmToolCall toolCall,
      JsonNode redactedResult,
      String serialized
  ) {
    if (policy.blobEnabled() && resultStore != null) {
      return resultStore.saveToolResult(
          context,
          stepIndex,
          toolCall,
          redactedResult,
          serialized,
          CONTENT_TYPE_JSON,
          ToolResultCompactionPolicy.POLICY_VERSION);
    }
    String fallbackRef = "tool-result:" + sha256(serialized).substring(0, 24);
    return new StoredToolResult(
        fallbackRef,
        CONTENT_TYPE_JSON,
        serialized,
        sha256(serialized),
        serialized.length(),
        lineCount(serialized),
        null,
        null);
  }

  private void addShape(ObjectNode visible, JsonNode result) {
    if (result.isObject()) {
      ArrayNode keys = objectMapper.createArrayNode();
      result.fieldNames().forEachRemaining(keys::add);
      visible.set("topLevelKeys", keys);
      return;
    }
    if (result.isArray()) {
      visible.put("arrayLength", result.size());
    }
  }

  private String canonicalJson(JsonNode node) {
    try {
      return objectMapper.writeValueAsString(node);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Failed to serialize tool result JSON", ex);
    }
  }

  private String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 digest is unavailable", ex);
    }
  }

  private int lineCount(String text) {
    if (text == null || text.isEmpty()) {
      return 0;
    }
    int lines = 1;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '\n') {
        lines++;
      }
    }
    return lines;
  }
}
