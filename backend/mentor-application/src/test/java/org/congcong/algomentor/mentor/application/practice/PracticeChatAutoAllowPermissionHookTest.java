package org.congcong.algomentor.mentor.application.practice;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.congcong.algomentor.agent.core.AgentExecutionContext;
import org.congcong.algomentor.agent.core.AgentLoopContext;
import org.congcong.algomentor.agent.core.AgentRequest;
import org.congcong.algomentor.agent.core.AgentTool;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionBehavior;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionCheck;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionDecisionPlan;
import org.congcong.algomentor.agent.core.permission.ToolNamePermissionHook;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.tool.LlmToolCall;
import org.congcong.algomentor.llm.core.tool.LlmToolSpec;
import org.junit.jupiter.api.Test;

class PracticeChatAutoAllowPermissionHookTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Test
  void allowsFormalCodeReviewFromTrustedPracticeChatMetadata() {
    PracticeChatAutoAllowPermissionHook hook = new PracticeChatAutoAllowPermissionHook();

    AgentToolPermissionDecisionPlan plan = hook.evaluate(check(
        PracticeCodeReviewAgentToolNames.SUBMIT_PRACTICE_CODE_REVIEW,
        Map.of(AgentRuntimeMetadataKeys.AGENT_KEY, PracticeChatAgentDefinition.KEY.value())));

    assertThat(hook.order()).isLessThan(ToolNamePermissionHook.DEFAULT_ORDER);
    assertThat(hook.order()).isLessThan(PracticeCodeReviewPermissionHook.DEFAULT_ORDER);
    assertThat(plan.behavior()).isEqualTo(AgentToolPermissionBehavior.ALLOW);
    assertThat(plan.policySource()).isEqualTo(PracticeChatAutoAllowPermissionHook.POLICY_SOURCE);
  }

  @Test
  void passesThroughForOtherToolsOtherAgentsOrAbsentAgentKey() {
    PracticeChatAutoAllowPermissionHook hook = new PracticeChatAutoAllowPermissionHook();

    assertThat(hook.evaluate(check(
        "get_current_problem",
        Map.of(AgentRuntimeMetadataKeys.AGENT_KEY, PracticeChatAgentDefinition.KEY.value()))).behavior())
        .isEqualTo(AgentToolPermissionBehavior.PASSTHROUGH);
    assertThat(hook.evaluate(check(
        PracticeCodeReviewAgentToolNames.SUBMIT_PRACTICE_CODE_REVIEW,
        Map.of(AgentRuntimeMetadataKeys.AGENT_KEY, "other-agent"))).behavior())
        .isEqualTo(AgentToolPermissionBehavior.PASSTHROUGH);
    assertThat(hook.evaluate(check(PracticeCodeReviewAgentToolNames.SUBMIT_PRACTICE_CODE_REVIEW, Map.of())).behavior())
        .isEqualTo(AgentToolPermissionBehavior.PASSTHROUGH);
  }

  private AgentToolPermissionCheck check(String toolName, Map<String, Object> trustedMetadata) {
    AgentRequest request = new AgentRequest("run-1", "idem-1", List.of(LlmMessage.user("hello")), trustedMetadata);
    AgentLoopContext context = new AgentLoopContext("run-1", request, 4, trustedMetadata);
    AgentTool tool = new AgentTool() {
      @Override
      public LlmToolSpec spec() {
        return new LlmToolSpec(toolName, "test", OBJECT_MAPPER.createObjectNode().put("type", "object"), true);
      }

      @Override
      public com.fasterxml.jackson.databind.JsonNode execute(
          com.fasterxml.jackson.databind.JsonNode arguments,
          AgentExecutionContext executionContext
      ) {
        return OBJECT_MAPPER.createObjectNode();
      }
    };
    return new AgentToolPermissionCheck(
        context,
        1,
        new LlmToolCall("call-1", toolName, OBJECT_MAPPER.createObjectNode()),
        tool,
        trustedMetadata);
  }
}
