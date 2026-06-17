package org.congcong.algomentor.agent.core.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.Optional;
import org.congcong.algomentor.agent.core.AgentExecutionContext;
import org.congcong.algomentor.agent.core.AgentLoopContext;
import org.congcong.algomentor.agent.core.compaction.ToolResultCompactionPolicy;
import org.congcong.algomentor.agent.core.toolresult.InMemoryToolResultStore;
import org.congcong.algomentor.agent.core.toolresult.StoredToolResult;
import org.congcong.algomentor.agent.core.toolresult.ToolResultStore;
import org.congcong.algomentor.llm.core.tool.LlmToolCall;
import org.junit.jupiter.api.Test;

class ReadToolResultToolTest {

  @Test
  void readsBoundedOffsetRange() {
    InMemoryToolResultStore store = new InMemoryToolResultStore();
    var stored = store.saveToolResult(
        null,
        1,
        new LlmToolCall("call_1", "lookup", JsonNodeFactory.instance.objectNode()),
        JsonNodeFactory.instance.objectNode(),
        "abcdefghijklmnopqrstuvwxyz",
        "text/plain",
        "test");
    ReadToolResultTool tool = new ReadToolResultTool(store, policy(5));

    var result = tool.execute(
        JsonNodeFactory.instance.objectNode()
            .put("resultRef", stored.resultRef())
            .put("offset", 2)
            .put("limit", 20),
        new AgentExecutionContext("run-id", 1, java.util.Map.of(), false));

    assertThat(result.get("content").asText()).isEqualTo("cdefg");
    assertThat(result.get("hasMoreBefore").asBoolean()).isTrue();
    assertThat(result.get("hasMoreAfter").asBoolean()).isTrue();
  }

  @Test
  void readsBoundedLineRange() {
    InMemoryToolResultStore store = new InMemoryToolResultStore();
    var stored = store.saveToolResult(
        null,
        1,
        new LlmToolCall("call_1", "lookup", JsonNodeFactory.instance.objectNode()),
        JsonNodeFactory.instance.objectNode(),
        "line1\nline2\nline3",
        "text/plain",
        "test");
    ReadToolResultTool tool = new ReadToolResultTool(store, policy(100));

    var result = tool.execute(
        JsonNodeFactory.instance.objectNode()
            .put("resultRef", stored.resultRef())
            .put("lineStart", 2)
            .put("lineEnd", 3),
        new AgentExecutionContext("run-id", 1, java.util.Map.of(), false));

    assertThat(result.get("content").asText()).isEqualTo("line2\nline3");
    assertThat(result.get("hasMoreBefore").asBoolean()).isTrue();
    assertThat(result.get("hasMoreAfter").asBoolean()).isFalse();
  }

  @Test
  void passesRunContextToStoreForPermissionCheck() {
    CapturingStore store = new CapturingStore(new StoredToolResult(
        "tool-result:1",
        "text/plain",
        "abcdef",
        "hash",
        6,
        1,
        1L,
        2L));
    ReadToolResultTool tool = new ReadToolResultTool(store, policy(3));

    tool.execute(
        JsonNodeFactory.instance.objectNode()
            .put("resultRef", "tool-result:1")
            .put("offset", 0)
            .put("limit", 3),
        new AgentExecutionContext("run-id", 1, java.util.Map.of("runDbId", 31L), false));

    assertThat(store.context.runId()).isEqualTo("run-id");
    assertThat(store.context.metadata()).containsEntry("runDbId", 31L);
  }

  private static final class CapturingStore implements ToolResultStore {
    private final StoredToolResult result;
    private AgentLoopContext context;

    private CapturingStore(StoredToolResult result) {
      this.result = result;
    }

    @Override
    public StoredToolResult saveToolResult(
        AgentLoopContext context,
        int stepIndex,
        LlmToolCall toolCall,
        com.fasterxml.jackson.databind.JsonNode redactedResult,
        String serializedResult,
        String contentType,
        String redactionPolicyVersion
    ) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<StoredToolResult> findByResultRef(AgentLoopContext context, String resultRef) {
      this.context = context;
      return Optional.of(result);
    }
  }

  private ToolResultCompactionPolicy policy(int rangeMaxChars) {
    return new ToolResultCompactionPolicy(
        12_000,
        2_000,
        rangeMaxChars,
        true,
        60_000,
        3,
        true,
        120_000,
        80,
        2,
        24,
        true,
        false);
  }
}
