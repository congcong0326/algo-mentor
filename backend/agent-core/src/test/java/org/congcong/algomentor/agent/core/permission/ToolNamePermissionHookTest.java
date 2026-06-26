package org.congcong.algomentor.agent.core.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
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

class ToolNamePermissionHookTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Test
  void evaluatesConfiguredBehaviorByToolName() {
    ToolNamePermissionHook hook = new ToolNamePermissionHook(Map.of(
        "safe_tool", AgentToolPermissionBehavior.ALLOW,
        "blocked_tool", AgentToolPermissionBehavior.DENY,
        "review_tool", AgentToolPermissionBehavior.ASK,
        "delegated_tool", AgentToolPermissionBehavior.PASSTHROUGH));

    AgentToolPermissionDecisionPlan allow = hook.evaluate(check("safe_tool"));
    AgentToolPermissionDecisionPlan deny = hook.evaluate(check("blocked_tool"));
    AgentToolPermissionDecisionPlan ask = hook.evaluate(check("review_tool"));
    AgentToolPermissionDecisionPlan delegated = hook.evaluate(check("delegated_tool"));
    AgentToolPermissionDecisionPlan unmatched = hook.evaluate(check("other_tool"));

    assertThat(allow.behavior()).isEqualTo(AgentToolPermissionBehavior.ALLOW);
    assertThat(allow.policySource()).isEqualTo(ToolNamePermissionHook.POLICY_SOURCE);
    assertThat(deny.behavior()).isEqualTo(AgentToolPermissionBehavior.DENY);
    assertThat(deny.reason()).isEqualTo("tool_name_policy_denied");
    assertThat(ask.behavior()).isEqualTo(AgentToolPermissionBehavior.ASK);
    assertThat(ask.displayName()).isEqualTo("Display review_tool");
    assertThat(ask.reason()).isEqualTo("该工具执行前需要用户确认。");
    assertThat(ask.preview()).containsEntry(ToolNamePermissionHook.PREVIEW_TOOL_NAME, "review_tool");
    assertThat(delegated.behavior()).isEqualTo(AgentToolPermissionBehavior.PASSTHROUGH);
    assertThat(unmatched.behavior()).isEqualTo(AgentToolPermissionBehavior.PASSTHROUGH);
  }

  @Test
  void askPreviewDoesNotCopyToolArgumentsByDefault() {
    ToolNamePermissionHook hook = new ToolNamePermissionHook(Map.of(
        "review_tool", AgentToolPermissionBehavior.ASK));

    AgentToolPermissionDecisionPlan plan = hook.evaluate(check("review_tool"));

    assertThat(plan.preview()).containsEntry(ToolNamePermissionHook.PREVIEW_TOOL_NAME, "review_tool");
    assertThat(plan.preview().toString()).doesNotContain("secret-code");
  }

  @Test
  void supportsExplicitRulesForToolNamePolicy() {
    ToolNamePermissionHook hook = new ToolNamePermissionHook(5, Map.of(
        "review_tool",
        ToolNamePermissionHook.ToolNamePermissionRule.ask(
            "提交代码 Review",
            "模型请求执行一次正式代码 Review。",
            Map.of("effect", "save_review"))));

    AgentToolPermissionDecisionPlan plan = hook.evaluate(check("review_tool"));

    assertThat(hook.order()).isEqualTo(5);
    assertThat(plan.behavior()).isEqualTo(AgentToolPermissionBehavior.ASK);
    assertThat(plan.displayName()).isEqualTo("提交代码 Review");
    assertThat(plan.reason()).isEqualTo("模型请求执行一次正式代码 Review。");
    assertThat(plan.preview()).containsEntry("effect", "save_review");
    assertThat(plan.policySource()).isEqualTo(ToolNamePermissionHook.POLICY_SOURCE);
  }

  @Test
  void copiesRulesDefensively() {
    Map<String, ToolNamePermissionHook.ToolNamePermissionRule> rules = new HashMap<>();
    rules.put("review_tool", ToolNamePermissionHook.ToolNamePermissionRule.deny("blocked"));
    ToolNamePermissionHook hook = new ToolNamePermissionHook(10, rules);
    rules.put("review_tool", ToolNamePermissionHook.ToolNamePermissionRule.allow());

    AgentToolPermissionDecisionPlan plan = hook.evaluate(check("review_tool"));

    assertThat(plan.behavior()).isEqualTo(AgentToolPermissionBehavior.DENY);
    assertThat(plan.reason()).isEqualTo("blocked");
    assertThatThrownBy(() -> hook.rules().put("x", ToolNamePermissionHook.ToolNamePermissionRule.allow()))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void rejectsInvalidInputs() {
    assertThatThrownBy(() -> new ToolNamePermissionHook(Map.of(" ", AgentToolPermissionBehavior.ALLOW)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tool name");
    assertThatThrownBy(() -> new ToolNamePermissionHook(Map.of("tool", null)))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new ToolNamePermissionHook(Map.of()).evaluate(null))
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
        OBJECT_MAPPER.createObjectNode().put("source", "secret-code"));
    return new AgentToolPermissionCheck(context, 1, toolCall, tool(toolName), request.metadata());
  }

  private static AgentTool tool(String name) {
    return new AgentTool() {
      @Override
      public LlmToolSpec spec() {
        return new LlmToolSpec(
            name,
            "Display " + name,
            OBJECT_MAPPER.createObjectNode().put("type", "object"),
            true);
      }

      @Override
      public JsonNode execute(JsonNode arguments, AgentExecutionContext context) {
        return OBJECT_MAPPER.createObjectNode().put("ok", true);
      }
    };
  }
}
