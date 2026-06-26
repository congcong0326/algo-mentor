package org.congcong.algomentor.agent.core.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
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

class AgentToolPermissionModelTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Test
  void askPlanRequiresDisplayReasonAndPreview() {
    AgentToolPermissionDecisionPlan plan = AgentToolPermissionDecisionPlan.ask(
        "提交代码 Review",
        "该工具会保存正式结果",
        Map.of("effect", "save_review"),
        "tool-name-policy");

    assertThat(plan.behavior()).isEqualTo(AgentToolPermissionBehavior.ASK);
    assertThat(plan.displayName()).isEqualTo("提交代码 Review");
    assertThat(plan.reason()).isEqualTo("该工具会保存正式结果");
    assertThat(plan.preview()).containsEntry("effect", "save_review");
    assertThat(plan.policySource()).isEqualTo("tool-name-policy");
    assertThat(plan.metadata()).isEmpty();

    assertThatThrownBy(() -> AgentToolPermissionDecisionPlan.ask(
        "提交代码 Review",
        "需要确认",
        Map.of(),
        "tool-name-policy"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("preview");
    assertThatThrownBy(() -> AgentToolPermissionDecisionPlan.ask(
        "",
        "需要确认",
        Map.of("effect", "save_review"),
        "tool-name-policy"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("display name");
    assertThatThrownBy(() -> AgentToolPermissionDecisionPlan.ask(
        "提交代码 Review",
        " ",
        Map.of("effect", "save_review"),
        "tool-name-policy"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reason");
  }

  @Test
  void passthroughCarriesNoDecisionDetails() {
    AgentToolPermissionDecisionPlan plan = AgentToolPermissionDecisionPlan.passthrough();

    assertThat(plan.behavior()).isEqualTo(AgentToolPermissionBehavior.PASSTHROUGH);
    assertThat(plan.displayName()).isNull();
    assertThat(plan.reason()).isNull();
    assertThat(plan.preview()).isEmpty();
    assertThat(plan.metadata()).isEmpty();

    assertThatThrownBy(() -> new AgentToolPermissionDecisionPlan(
        AgentToolPermissionBehavior.PASSTHROUGH,
        "Tool",
        null,
        Map.of(),
        "policy"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("passthrough");
  }

  @Test
  void planCopiesMetadata() {
    Map<String, Object> metadata = new java.util.HashMap<>();
    metadata.put(AgentToolPermissionMetadataKeys.PERMISSION_HOOK_ERROR_TYPE, "IllegalStateException");

    AgentToolPermissionDecisionPlan plan = AgentToolPermissionDecisionPlan.deny(
        "permission_hook_failed",
        "permission-hook-failure:broken-hook",
        metadata);
    metadata.put(AgentToolPermissionMetadataKeys.PERMISSION_HOOK_ERROR_TYPE, "changed");

    assertThat(plan.metadata())
        .containsEntry(AgentToolPermissionMetadataKeys.PERMISSION_HOOK_ERROR_TYPE, "IllegalStateException");
    assertThatThrownBy(() -> plan.metadata().put("x", "y"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void checkCopiesTrustedMetadata() {
    Map<String, Object> trustedMetadata = new java.util.HashMap<>();
    trustedMetadata.put(AgentRuntimeMetadataKeys.USER_ID, 7L);

    AgentToolPermissionCheck check = new AgentToolPermissionCheck(
        context(),
        1,
        toolCall(),
        tool(),
        trustedMetadata);
    trustedMetadata.put(AgentRuntimeMetadataKeys.USER_ID, 99L);

    assertThat(check.trustedMetadata()).containsEntry(AgentRuntimeMetadataKeys.USER_ID, 7L);
    assertThatThrownBy(() -> check.trustedMetadata().put("x", "y"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void requestAndDecisionValidateAndCopyValues() {
    Map<String, Object> preview = new java.util.HashMap<>();
    preview.put("tool", "review");
    Instant createdAt = Instant.parse("2026-06-26T00:00:00Z");
    AgentToolPermissionRequest request = new AgentToolPermissionRequest(
        "perm-1",
        "run-1",
        2,
        "call-1",
        "submit_practice_code_review",
        "提交代码 Review",
        "会保存正式 Review",
        preview,
        createdAt,
        createdAt.plusSeconds(60));
    preview.put("tool", "changed");

    assertThat(request.preview()).containsEntry("tool", "review");
    assertThatThrownBy(() -> request.preview().put("x", "y"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> new AgentToolPermissionRequest(
        "perm-1",
        "run-1",
        1,
        "call-1",
        "tool",
        "Tool",
        "Reason",
        Map.of("preview", true),
        createdAt,
        createdAt))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("after created");

    AgentToolPermissionDecision decision = new AgentToolPermissionDecision(
        "perm-1",
        AgentToolPermissionDecisionType.ALLOW,
        "user_confirmed",
        7L,
        createdAt.plusSeconds(5));

    assertThat(decision.permissionRequestId()).isEqualTo("perm-1");
    assertThat(decision.decision()).isEqualTo(AgentToolPermissionDecisionType.ALLOW);
    assertThatThrownBy(() -> new AgentToolPermissionDecision(
        "perm-1",
        null,
        "user_confirmed",
        7L,
        createdAt.plusSeconds(5)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("decision type");
  }

  @Test
  void authorizationSeparatesAllowedAndSyntheticResult() {
    AgentToolPermissionDecisionPlan allowPlan = AgentToolPermissionDecisionPlan.allow("default");
    AgentToolPermissionAuthorization.Allowed allowed = new AgentToolPermissionAuthorization.Allowed(allowPlan);

    assertThat(allowed.plan()).isSameAs(allowPlan);
    AgentToolPermissionDecisionPlan askPlan = AgentToolPermissionDecisionPlan.ask(
        "提交代码 Review",
        "用户确认后执行",
        Map.of("effect", "save_review"),
        "policy");
    AgentToolPermissionAuthorization.Allowed allowedAfterAsk =
        new AgentToolPermissionAuthorization.Allowed(askPlan);
    assertThat(allowedAfterAsk.plan()).isSameAs(askPlan);

    JsonNode result = OBJECT_MAPPER.createObjectNode().put("type", "tool_permission_denied");
    AgentToolPermissionDecisionPlan denyPlan = AgentToolPermissionDecisionPlan.deny("policy_denied", "policy");
    AgentToolPermissionAuthorization.SyntheticResult syntheticResult =
        new AgentToolPermissionAuthorization.SyntheticResult(result, denyPlan);

    assertThat(syntheticResult.result()).isSameAs(result);
    assertThat(syntheticResult.plan()).isSameAs(denyPlan);
    assertThatThrownBy(() -> new AgentToolPermissionAuthorization.SyntheticResult(result, allowPlan))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("DENY or ASK");
    assertThatThrownBy(() -> new AgentToolPermissionAuthorization.Allowed(denyPlan))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ALLOW or ASK plan");
  }

  @Test
  void metadataKeysAreCentralizedConstants() {
    assertThat(AgentToolPermissionMetadataKeys.PERMISSION_REQUEST_ID).isEqualTo("permissionRequestId");
    assertThat(AgentToolPermissionMetadataKeys.PERMISSION_BEHAVIOR).isEqualTo("permissionBehavior");
    assertThat(AgentToolPermissionMetadataKeys.PERMISSION_DECISION).isEqualTo("permissionDecision");
    assertThat(AgentToolPermissionMetadataKeys.PERMISSION_DECISION_REASON).isEqualTo("permissionDecisionReason");
    assertThat(AgentToolPermissionMetadataKeys.PERMISSION_TIMEOUT).isEqualTo("permissionTimeout");
    assertThat(AgentToolPermissionMetadataKeys.PERMISSION_LATENCY_MS).isEqualTo("permissionLatencyMs");
    assertThat(AgentToolPermissionMetadataKeys.PERMISSION_POLICY_SOURCE).isEqualTo("permissionPolicySource");
    assertThat(AgentToolPermissionMetadataKeys.PERMISSION_HOOK_NAME).isEqualTo("permissionHookName");
    assertThat(AgentToolPermissionMetadataKeys.PERMISSION_HOOK_ERROR_TYPE).isEqualTo("permissionHookErrorType");
    assertThat(AgentToolPermissionMetadataKeys.PERMISSION_OWNER_USER_ID).isEqualTo("permissionOwnerUserId");
  }

  private AgentLoopContext context() {
    AgentRequest request = new AgentRequest(
        "run-1",
        "request-1",
        List.of(LlmMessage.user("hello")),
        Map.of(AgentRuntimeMetadataKeys.USER_ID, 7L));
    return new AgentLoopContext("run-1", request, 4, request.metadata());
  }

  private LlmToolCall toolCall() {
    return new LlmToolCall(
        "call-1",
        "tool_a",
        OBJECT_MAPPER.createObjectNode().put("value", 1));
  }

  private AgentTool tool() {
    return new AgentTool() {
      @Override
      public LlmToolSpec spec() {
        return new LlmToolSpec(
            "tool_a",
            "Test tool",
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
