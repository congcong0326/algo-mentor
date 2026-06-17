package org.congcong.algomentor.agent.core.compaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.ArrayList;
import java.util.List;
import org.congcong.algomentor.agent.core.AgentLoopContext;
import org.congcong.algomentor.agent.core.AgentRequest;
import org.congcong.algomentor.agent.core.toolresult.InMemoryToolResultStore;
import org.congcong.algomentor.llm.core.request.LlmContentPart;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.tool.LlmToolCall;
import org.junit.jupiter.api.Test;

class RunMessageCompactorTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void compactsOldToolResultsWithoutBreakingToolCallPairing() {
    RunMessageCompactor compactor = compactor(policy(40, 1, 1_000, 80, 2, 10));
    LlmToolCall first = new LlmToolCall("call_1", "lookup", JsonNodeFactory.instance.objectNode());
    LlmToolCall second = new LlmToolCall("call_2", "lookup", JsonNodeFactory.instance.objectNode());
    List<LlmMessage> messages = new ArrayList<>();
    messages.add(LlmMessage.user("question"));
    messages.add(LlmMessage.assistantToolCalls(List.of(first)));
    messages.add(LlmMessage.toolResult("call_1", JsonNodeFactory.instance.objectNode()
        .put("type", "tool_result_preview")
        .put("resultRef", "tool-result:1")
        .put("toolName", "lookup")
        .put("payload", "abcdefghijklmnopqrstuvwxyz")));
    messages.add(LlmMessage.assistantToolCalls(List.of(second)));
    messages.add(LlmMessage.toolResult("call_2", JsonNodeFactory.instance.objectNode()
        .put("payload", "abcdefghijklmnopqrstuvwxyz")));

    RunMessageCompactionResult result = compactor.compactBeforeRequest(context(), 2, messages);

    assertThat(result.messages()).hasSize(5);
    assertThat(result.messages().get(1).toolCalls()).containsExactly(first);
    assertThat(result.messages().get(2).toolCallId()).isEqualTo("call_1");
    var toolResult = (LlmContentPart.ToolResult) result.messages().get(2).content().get(0);
    assertThat(toolResult.result().get("type").asText()).isEqualTo("tool_result_compacted");
    assertThat(result.messages().get(4).toolCallId()).isEqualTo("call_2");
  }

  @Test
  void snipsMiddleGroupsAndKeepsHeadAndTail() {
    RunMessageCompactor compactor = compactor(policy(10_000, 0, 20, 4, 1, 2));
    List<LlmMessage> messages = new ArrayList<>();
    messages.add(LlmMessage.system("system"));
    messages.add(LlmMessage.user("old one"));
    messages.add(LlmMessage.assistant("old two"));
    messages.add(LlmMessage.user("middle"));
    messages.add(LlmMessage.assistant("recent assistant"));
    messages.add(LlmMessage.user("recent user"));

    RunMessageCompactionResult result = compactor.compactBeforeRequest(context(), 3, messages);

    assertThat(result.messages().get(0).role()).isEqualTo(LlmMessage.Role.SYSTEM);
    assertThat(result.messages()).anySatisfy(message -> assertThat(message.text()).contains("run_context_snip"));
    assertThat(result.messages().get(result.messages().size() - 1).text()).isEqualTo("recent user");
    assertThat(result.metadata()).containsKey("runContextCompaction");
  }

  private RunMessageCompactor compactor(ToolResultCompactionPolicy policy) {
    ToolResultCompactor toolResultCompactor = new ToolResultCompactor(
        objectMapper,
        policy,
        new InMemoryToolResultStore());
    return new RunMessageCompactor(objectMapper, toolResultCompactor);
  }

  private ToolResultCompactionPolicy policy(
      int totalToolChars,
      int keepRecent,
      int inputTokenBudget,
      int maxGroups,
      int keepHead,
      int keepTail
  ) {
    return new ToolResultCompactionPolicy(
        12_000,
        2_000,
        8_000,
        true,
        totalToolChars,
        keepRecent,
        true,
        inputTokenBudget,
        maxGroups,
        keepHead,
        keepTail,
        true,
        false);
  }

  private AgentLoopContext context() {
    AgentRequest request = new AgentRequest(java.util.List.of(LlmMessage.user("question")));
    return new AgentLoopContext("run-id", request, 4, request.metadata());
  }
}
