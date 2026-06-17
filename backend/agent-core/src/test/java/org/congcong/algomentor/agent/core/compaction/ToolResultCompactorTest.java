package org.congcong.algomentor.agent.core.compaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.congcong.algomentor.agent.core.AgentLoopContext;
import org.congcong.algomentor.agent.core.AgentRequest;
import org.congcong.algomentor.agent.core.toolresult.InMemoryToolResultStore;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.tool.LlmToolCall;
import org.junit.jupiter.api.Test;

class ToolResultCompactorTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final InMemoryToolResultStore store = new InMemoryToolResultStore();

  @Test
  void keepsSmallToolResultInline() {
    ToolResultCompactor compactor = new ToolResultCompactor(
        objectMapper,
        policy(100, 20),
        store);
    var result = JsonNodeFactory.instance.objectNode().put("value", 42);

    ToolResultCompaction compaction = compactor.compactForModel(context(), 1, toolCall(), result);

    assertThat(compaction.visibleResult()).isEqualTo(result);
    assertThat(compaction.metadata().storageMode()).isEqualTo("inline");
    assertThat(compaction.metadata().truncated()).isFalse();
  }

  @Test
  void convertsLargeToolResultToPreviewAndStoresFullText() {
    ToolResultCompactor compactor = new ToolResultCompactor(
        objectMapper,
        policy(10, 8),
        store);
    var result = JsonNodeFactory.instance.objectNode().put("payload", "abcdefghijklmnopqrstuvwxyz");

    ToolResultCompaction compaction = compactor.compactForModel(context(), 1, toolCall(), result);

    assertThat(compaction.visibleResult().get("type").asText()).isEqualTo("tool_result_preview");
    assertThat(compaction.visibleResult().get("preview").asText()).hasSize(8);
    assertThat(compaction.visibleResult().get("resultRef").asText()).startsWith("tool-result:");
    assertThat(compaction.metadata().storageMode()).isEqualTo("blob");
    assertThat(store.findByResultRef(compaction.visibleResult().get("resultRef").asText()))
        .hasValueSatisfying(stored -> assertThat(stored.contentText()).contains("abcdefghijklmnopqrstuvwxyz"));
  }

  private ToolResultCompactionPolicy policy(int inlineMaxChars, int previewMaxChars) {
    return new ToolResultCompactionPolicy(
        inlineMaxChars,
        previewMaxChars,
        100,
        true,
        1_000,
        3,
        true,
        1_000,
        80,
        2,
        24,
        true,
        false);
  }

  private AgentLoopContext context() {
    AgentRequest request = new AgentRequest(java.util.List.of(LlmMessage.user("question")));
    return new AgentLoopContext("run-id", request, 4, request.metadata());
  }

  private LlmToolCall toolCall() {
    return new LlmToolCall("call_1", "lookup", JsonNodeFactory.instance.objectNode());
  }
}
