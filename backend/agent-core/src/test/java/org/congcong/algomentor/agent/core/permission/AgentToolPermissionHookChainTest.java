package org.congcong.algomentor.agent.core.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.congcong.algomentor.agent.core.AgentExecutionContext;
import org.congcong.algomentor.agent.core.AgentLoopContext;
import org.congcong.algomentor.agent.core.AgentRequest;
import org.congcong.algomentor.agent.core.AgentTool;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.tool.LlmToolCall;
import org.congcong.algomentor.llm.core.tool.LlmToolSpec;
import org.junit.jupiter.api.Test;

class AgentToolPermissionHookChainTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Test
  void evaluatesHooksByOrderAndStopsAtFirstDecision() {
    List<String> calls = new ArrayList<>();
    RecordingMetrics metrics = new RecordingMetrics();
    AgentToolPermissionHook lateAllow = new RecordingHook(
        "late",
        30,
        AgentToolPermissionDecisionPlan.allow("late-policy"),
        calls);
    AgentToolPermissionHook earlyPassthrough = new RecordingHook(
        "early",
        10,
        AgentToolPermissionDecisionPlan.passthrough(),
        calls);
    AgentToolPermissionHook middleAsk = new RecordingHook(
        "middle",
        20,
        AgentToolPermissionDecisionPlan.ask(
            "需要确认",
            "该工具会产生外部影响",
            Map.of("toolName", "review"),
            "middle-policy"),
        calls);

    AgentToolPermissionDecisionPlan plan = new AgentToolPermissionHookChain(
        List.of(lateAllow, earlyPassthrough, middleAsk),
        metrics).evaluate(check("review"));

    assertThat(calls).containsExactly("early", "middle");
    assertThat(plan.behavior()).isEqualTo(AgentToolPermissionBehavior.ASK);
    assertThat(plan.policySource()).isEqualTo("middle-policy");
    assertThat(metrics.records).containsExactly("hook:review:ASK:middle-policy");
  }

  @Test
  void fallsBackToDefaultAllowWhenEveryHookPassesThrough() {
    List<String> calls = new ArrayList<>();
    AgentToolPermissionHookChain chain = new AgentToolPermissionHookChain(List.of(
        new RecordingHook("first", 10, AgentToolPermissionDecisionPlan.passthrough(), calls),
        new RecordingHook("second", 20, AgentToolPermissionDecisionPlan.passthrough(), calls)));

    AgentToolPermissionDecisionPlan plan = chain.evaluate(check("read_only_tool"));

    assertThat(calls).containsExactly("first", "second");
    assertThat(plan.behavior()).isEqualTo(AgentToolPermissionBehavior.ALLOW);
    assertThat(plan.policySource()).isEqualTo(DefaultPermissionHook.POLICY_SOURCE);
  }

  @Test
  void emptyChainUsesDefaultAllow() {
    AgentToolPermissionDecisionPlan plan = new AgentToolPermissionHookChain().evaluate(check("calculator"));

    assertThat(plan.behavior()).isEqualTo(AgentToolPermissionBehavior.ALLOW);
    assertThat(plan.policySource()).isEqualTo(DefaultPermissionHook.POLICY_SOURCE);
  }

  @Test
  void hookExceptionFailsClosedWithoutSensitiveErrorMessage() {
    RecordingMetrics metrics = new RecordingMetrics();
    AgentToolPermissionHookChain chain = new AgentToolPermissionHookChain(List.of(new ThrowingHook()), metrics);

    AgentToolPermissionDecisionPlan plan = chain.evaluate(check("submit_review"));

    assertThat(plan.behavior()).isEqualTo(AgentToolPermissionBehavior.DENY);
    assertThat(plan.reason()).isEqualTo("permission_hook_failed");
    assertThat(plan.policySource()).isEqualTo("permission-hook-failure:ThrowingHook");
    assertThat(plan.metadata())
        .containsEntry(AgentToolPermissionMetadataKeys.PERMISSION_HOOK_NAME, "ThrowingHook")
        .containsEntry(AgentToolPermissionMetadataKeys.PERMISSION_HOOK_ERROR_TYPE, "IllegalStateException");
    assertThat(plan.metadata().toString()).doesNotContain("secret-token");
    assertThat(metrics.records)
        .containsExactly("hook:submit_review:DENY:permission-hook-failure:ThrowingHook");
  }

  @Test
  void nullHookResultFailsClosed() {
    AgentToolPermissionHookChain chain = new AgentToolPermissionHookChain(List.of(new NullReturningHook()));

    AgentToolPermissionDecisionPlan plan = chain.evaluate(check("submit_review"));

    assertThat(plan.behavior()).isEqualTo(AgentToolPermissionBehavior.DENY);
    assertThat(plan.policySource()).isEqualTo("permission-hook-failure:NullReturningHook");
    assertThat(plan.metadata())
        .containsEntry(AgentToolPermissionMetadataKeys.PERMISSION_HOOK_ERROR_TYPE, "NullPointerException");
  }

  @Test
  void rejectsNullHooksAndNullCheck() {
    assertThatThrownBy(() -> new AgentToolPermissionHookChain(Collections.singletonList(null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("hook");

    assertThatThrownBy(() -> new AgentToolPermissionHookChain().evaluate(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("check");
  }

  private static AgentToolPermissionCheck check(String toolName) {
    AgentRequest request = new AgentRequest(
        "run-1",
        "request-1",
        List.of(LlmMessage.user("hello")),
        Map.of(AgentRuntimeMetadataKeys.USER_ID, 7L));
    AgentLoopContext context = new AgentLoopContext("run-1", request, 4, request.metadata());
    LlmToolCall toolCall = new LlmToolCall(
        "call-1",
        toolName,
        OBJECT_MAPPER.createObjectNode().put("secret", "secret-token"));
    return new AgentToolPermissionCheck(context, 1, toolCall, tool(toolName), request.metadata());
  }

  private static AgentTool tool(String name) {
    return new AgentTool() {
      @Override
      public LlmToolSpec spec() {
        return new LlmToolSpec(
            name,
            "Test tool " + name,
            OBJECT_MAPPER.createObjectNode().put("type", "object"),
            true);
      }

      @Override
      public JsonNode execute(JsonNode arguments, AgentExecutionContext context) {
        return OBJECT_MAPPER.createObjectNode().put("ok", true);
      }
    };
  }

  private record RecordingHook(
      String name,
      int order,
      AgentToolPermissionDecisionPlan plan,
      List<String> calls
  ) implements AgentToolPermissionHook {

    @Override
    public AgentToolPermissionDecisionPlan evaluate(AgentToolPermissionCheck check) {
      calls.add(name);
      return plan;
    }
  }

  private static final class ThrowingHook implements AgentToolPermissionHook {

    @Override
    public int order() {
      return 10;
    }

    @Override
    public AgentToolPermissionDecisionPlan evaluate(AgentToolPermissionCheck check) {
      throw new IllegalStateException("secret-token must not leak");
    }
  }

  private static final class NullReturningHook implements AgentToolPermissionHook {

    @Override
    public int order() {
      return 10;
    }

    @Override
    public AgentToolPermissionDecisionPlan evaluate(AgentToolPermissionCheck check) {
      return null;
    }
  }

  private static final class RecordingMetrics implements AgentToolPermissionMetrics {

    private final List<String> records = new ArrayList<>();

    @Override
    public void recordHookDecision(
        String toolName,
        AgentToolPermissionBehavior behavior,
        String policySource
    ) {
      records.add("hook:" + toolName + ":" + behavior + ":" + policySource);
    }

    @Override
    public void recordPermissionRequest(
        String toolName,
        String policySource
    ) {
    }

    @Override
    public void recordUserDecision(
        String toolName,
        AgentToolPermissionDecisionType decision
    ) {
    }

    @Override
    public void recordTimeout(String toolName) {
    }

    @Override
    public void recordLatency(
        String toolName,
        String outcome,
        java.time.Duration latency
    ) {
    }

    @Override
    public void recordHighPermissionExecution(
        String toolName,
        String policySource
    ) {
    }
  }
}
