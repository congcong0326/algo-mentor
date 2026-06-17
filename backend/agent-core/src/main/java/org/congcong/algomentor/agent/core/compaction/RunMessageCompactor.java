package org.congcong.algomentor.agent.core.compaction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.congcong.algomentor.agent.core.AgentLoopContext;
import org.congcong.algomentor.llm.core.request.LlmContentPart;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.tool.LlmToolCall;

public final class RunMessageCompactor {

  private static final String COMPACTION_METADATA_KEY = "runContextCompaction";

  private final ObjectMapper objectMapper;
  private final ToolResultCompactor toolResultCompactor;
  private final ToolResultCompactionPolicy policy;

  public RunMessageCompactor(ObjectMapper objectMapper, ToolResultCompactor toolResultCompactor) {
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    this.toolResultCompactor = Objects.requireNonNull(toolResultCompactor, "toolResultCompactor must not be null");
    this.policy = toolResultCompactor.policy();
  }

  public RunMessageCompactionResult compactBeforeRequest(
      AgentLoopContext context,
      int stepIndex,
      List<LlmMessage> messages
  ) {
    if (messages == null || messages.isEmpty()) {
      throw new IllegalArgumentException("messages must not be empty");
    }
    List<LlmMessage> compacted = new ArrayList<>(messages);
    Map<String, Object> metadata = new LinkedHashMap<>();
    int beforeChars = visibleCharCount(compacted);
    List<RunMessageGroup> groups = parseGroups(compacted);
    metadata.put("policyVersion", ToolResultCompactionPolicy.POLICY_VERSION);
    metadata.put("beforeCharCount", beforeChars);
    metadata.put("beforeGroupCount", groups.size());

    int compactedToolResults = compactOldToolResults(compacted, metadata);
    groups = parseGroups(compacted);
    int snippedGroups = snipGroupsIfNeeded(context, stepIndex, compacted, groups, metadata);
    int afterChars = visibleCharCount(compacted);

    metadata.put("afterCharCount", afterChars);
    metadata.put("afterGroupCount", parseGroups(compacted).size());
    metadata.put("compactedToolResults", compactedToolResults);
    metadata.put("snippedGroups", snippedGroups);
    if (compactedToolResults == 0 && snippedGroups == 0) {
      return new RunMessageCompactionResult(compacted, Map.of());
    }
    return new RunMessageCompactionResult(compacted, Map.of(COMPACTION_METADATA_KEY, metadata));
  }

  List<RunMessageGroup> parseGroups(List<LlmMessage> messages) {
    List<RunMessageGroup> groups = new ArrayList<>();
    Map<String, LlmToolCall> pendingToolCalls = new HashMap<>();
    int groupStart = 0;
    for (int index = 0; index < messages.size(); index++) {
      LlmMessage message = messages.get(index);
      if (message.role() == LlmMessage.Role.SYSTEM) {
        groups.add(new RunMessageGroup(GroupType.SYSTEM_GROUP, index, index + 1, false, false));
        groupStart = index + 1;
        continue;
      }
      List<LlmToolCall> toolCalls = message.toolCalls();
      if (message.role() == LlmMessage.Role.ASSISTANT && !toolCalls.isEmpty()) {
        pendingToolCalls.clear();
        toolCalls.forEach(toolCall -> pendingToolCalls.put(toolCall.id(), toolCall));
        groupStart = index;
        Set<String> seenResults = new HashSet<>();
        int end = index + 1;
        while (end < messages.size()
            && messages.get(end).role() == LlmMessage.Role.TOOL
            && pendingToolCalls.containsKey(messages.get(end).toolCallId())) {
          seenResults.add(messages.get(end).toolCallId());
          end++;
        }
        boolean missingResult = !seenResults.containsAll(pendingToolCalls.keySet());
        groups.add(new RunMessageGroup(GroupType.TOOL_INTERACTION_GROUP, groupStart, end, missingResult, false));
        index = end - 1;
        groupStart = end;
        continue;
      }
      if (message.role() == LlmMessage.Role.TOOL) {
        groups.add(new RunMessageGroup(GroupType.TOOL_INTERACTION_GROUP, index, index + 1, false, true));
        groupStart = index + 1;
        continue;
      }
      if (isCompactMarker(message)) {
        groups.add(new RunMessageGroup(GroupType.COMPACT_MARKER_GROUP, index, index + 1, false, false));
        groupStart = index + 1;
        continue;
      }
      groups.add(new RunMessageGroup(GroupType.PLAIN_EXCHANGE_GROUP, index, index + 1, false, false));
      groupStart = index + 1;
    }
    return groups;
  }

  private int compactOldToolResults(List<LlmMessage> messages, Map<String, Object> metadata) {
    if (!policy.compactOldToolResults()) {
      return 0;
    }
    List<Integer> toolIndexes = new ArrayList<>();
    for (int index = 0; index < messages.size(); index++) {
      if (messages.get(index).role() == LlmMessage.Role.TOOL) {
        toolIndexes.add(index);
      }
    }
    int total = toolIndexes.stream().mapToInt(index -> messageCharCount(messages.get(index))).sum();
    if (total <= policy.toolResultsTotalMaxChars()) {
      metadata.put("toolResultCharCount", total);
      return 0;
    }
    int keepFrom = Math.max(0, toolIndexes.size() - policy.keepRecentToolResults());
    int compacted = 0;
    List<String> compactedToolCallIds = new ArrayList<>();
    for (int ordinal = 0; ordinal < keepFrom && total > policy.toolResultsTotalMaxChars(); ordinal++) {
      int messageIndex = toolIndexes.get(ordinal);
      LlmMessage message = messages.get(messageIndex);
      JsonNode result = toolResult(message);
      if (result == null || isCompactedToolResult(result)) {
        continue;
      }
      String resultRef = textField(result, "resultRef");
      String toolName = textField(result, "toolName");
      JsonNode placeholder = toolResultCompactor.compactedPlaceholder(message.toolCallId(), toolName, resultRef);
      LlmMessage compactedMessage = LlmMessage.toolResult(message.toolCallId(), placeholder);
      total -= messageCharCount(message);
      total += messageCharCount(compactedMessage);
      messages.set(messageIndex, compactedMessage);
      compacted++;
      compactedToolCallIds.add(message.toolCallId());
    }
    metadata.put("toolResultCharCount", total);
    metadata.put("compactedToolCallIds", compactedToolCallIds);
    return compacted;
  }

  private int snipGroupsIfNeeded(
      AgentLoopContext context,
      int stepIndex,
      List<LlmMessage> messages,
      List<RunMessageGroup> groups,
      Map<String, Object> metadata
  ) {
    if (!policy.groupAwareSnipEnabled()) {
      return 0;
    }
    int visibleChars = visibleCharCount(messages);
    if (visibleChars <= policy.estimatedInputCharBudget() && groups.size() <= policy.maxMessageGroups()) {
      return 0;
    }
    int keepHead = Math.min(policy.snipKeepHeadGroups(), groups.size());
    int keepTailStart = Math.max(keepHead, groups.size() - policy.snipKeepTailGroups());
    List<RunMessageGroup> candidates = groups.subList(keepHead, keepTailStart)
        .stream()
        .filter(group -> !group.missingToolResult() && !group.orphanToolResult())
        .sorted(Comparator.comparingInt(RunMessageGroup::startIndex))
        .toList();
    if (candidates.isEmpty()) {
      metadata.put("snipSkipped", true);
      return 0;
    }

    List<LlmMessage> rebuilt = new ArrayList<>();
    for (int i = 0; i < groups.get(keepHead).startIndex(); i++) {
      rebuilt.add(messages.get(i));
    }
    ObjectNode marker = objectMapper.createObjectNode();
    marker.put("type", "run_context_snip");
    marker.put("snippedGroupCount", candidates.size());
    marker.put("message", "Earlier run context was snipped after tool results were compacted.");
    ObjectNode source = objectMapper.createObjectNode();
    source.put("runId", context.runId());
    source.put("toStepIndex", stepIndex);
    marker.set("source", source);
    rebuilt.add(LlmMessage.assistant(marker.toString()));
    for (int i = groups.get(keepTailStart).startIndex(); i < messages.size(); i++) {
      rebuilt.add(messages.get(i));
    }
    messages.clear();
    messages.addAll(rebuilt);
    return candidates.size();
  }

  private boolean isCompactMarker(LlmMessage message) {
    return message.role() == LlmMessage.Role.ASSISTANT && message.text().contains("run_context_snip");
  }

  private JsonNode toolResult(LlmMessage message) {
    if (message.role() != LlmMessage.Role.TOOL || message.content().isEmpty()) {
      return null;
    }
    LlmContentPart part = message.content().get(0);
    if (part instanceof LlmContentPart.ToolResult toolResult) {
      return toolResult.result();
    }
    return null;
  }

  private boolean isCompactedToolResult(JsonNode result) {
    return result.isObject() && "tool_result_compacted".equals(textField(result, "type"));
  }

  private String textField(JsonNode node, String fieldName) {
    JsonNode value = node == null ? null : node.get(fieldName);
    return value == null || !value.isTextual() ? null : value.asText();
  }

  private int visibleCharCount(List<LlmMessage> messages) {
    return messages.stream().mapToInt(this::messageCharCount).sum();
  }

  private int messageCharCount(LlmMessage message) {
    int count = message.text().length();
    for (LlmContentPart part : message.content()) {
      if (part instanceof LlmContentPart.ToolResult toolResult) {
        count += toolResult.result().toString().length();
      }
    }
    return count;
  }

  enum GroupType {
    SYSTEM_GROUP,
    PLAIN_EXCHANGE_GROUP,
    TOOL_INTERACTION_GROUP,
    COMPACT_MARKER_GROUP
  }

  record RunMessageGroup(
      GroupType type,
      int startIndex,
      int endIndex,
      boolean missingToolResult,
      boolean orphanToolResult
  ) {
  }
}
