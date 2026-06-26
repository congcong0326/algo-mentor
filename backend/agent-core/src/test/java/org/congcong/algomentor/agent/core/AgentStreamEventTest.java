package org.congcong.algomentor.agent.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionDecision;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionDecisionType;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionRequest;
import org.junit.jupiter.api.Test;

class AgentStreamEventTest {

  @Test
  void exposesPermissionEventNames() {
    assertThat(AgentStreamEventNames.TOOL_PERMISSION_REQUEST).isEqualTo("tool_permission_request");
    assertThat(AgentStreamEventNames.TOOL_PERMISSION_DECISION).isEqualTo("tool_permission_decision");
    assertThat(AgentStreamEventNames.TOOL_PERMISSION_TIMEOUT).isEqualTo("tool_permission_timeout");
  }

  @Test
  void permissionRequestEventUsesStableContract() {
    AgentStreamEvent.ToolPermissionRequest event =
        new AgentStreamEvent.ToolPermissionRequest(request());

    assertThat(event.name()).isEqualTo(AgentStreamEventNames.TOOL_PERMISSION_REQUEST);
    assertThat(event.permissionRequestId()).isEqualTo("perm-1");
    assertThat(event.runId()).isEqualTo("run-1");
    assertThat(event.stepIndex()).isEqualTo(2);
    assertThat(event.toolCallId()).isEqualTo("call-1");
    assertThat(event.toolName()).isEqualTo("submit_practice_code_review");
    assertThat(event.preview()).containsEntry("effect", "save_review");
    assertThat(event.expiresAt()).isEqualTo(Instant.parse("2026-06-26T00:01:00Z"));
  }

  @Test
  void permissionDecisionEventUsesStableContract() {
    AgentToolPermissionDecision decision = new AgentToolPermissionDecision(
        "perm-1",
        AgentToolPermissionDecisionType.ALLOW,
        "user_confirmed",
        7L,
        Instant.parse("2026-06-26T00:00:10Z"));

    AgentStreamEvent.ToolPermissionDecision event =
        new AgentStreamEvent.ToolPermissionDecision(request(), decision);

    assertThat(event.name()).isEqualTo(AgentStreamEventNames.TOOL_PERMISSION_DECISION);
    assertThat(event.permissionRequestId()).isEqualTo("perm-1");
    assertThat(event.decision()).isEqualTo(AgentToolPermissionDecisionType.ALLOW);
    assertThat(event.reason()).isEqualTo("user_confirmed");
    assertThat(event.decidedAt()).isEqualTo(Instant.parse("2026-06-26T00:00:10Z"));
  }

  @Test
  void permissionTimeoutEventUsesStableContract() {
    AgentStreamEvent.ToolPermissionTimeout event = new AgentStreamEvent.ToolPermissionTimeout(
        request(),
        "timeout",
        Instant.parse("2026-06-26T00:01:00Z"));

    assertThat(event.name()).isEqualTo(AgentStreamEventNames.TOOL_PERMISSION_TIMEOUT);
    assertThat(event.permissionRequestId()).isEqualTo("perm-1");
    assertThat(event.reason()).isEqualTo("timeout");
    assertThat(event.expiredAt()).isEqualTo(Instant.parse("2026-06-26T00:01:00Z"));
  }

  @Test
  void permissionEventsValidateRequiredFields() {
    assertThatThrownBy(() -> new AgentStreamEvent.ToolPermissionRequest(
        "",
        "run-1",
        1,
        "call-1",
        "tool",
        "Tool",
        "Reason",
        Map.of("effect", "save_review"),
        Instant.now()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("request id");
    assertThatThrownBy(() -> new AgentStreamEvent.ToolPermissionDecision(
        "perm-1",
        "run-1",
        1,
        "call-1",
        "tool",
        null,
        "user_confirmed",
        Instant.now()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("decision");
    assertThatThrownBy(() -> new AgentStreamEvent.ToolPermissionTimeout(
        "perm-1",
        "run-1",
        0,
        "call-1",
        "tool",
        "timeout",
        Instant.now()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("step index");
  }

  private static AgentToolPermissionRequest request() {
    return new AgentToolPermissionRequest(
        "perm-1",
        "run-1",
        2,
        "call-1",
        "submit_practice_code_review",
        "提交代码 Review",
        "模型请求执行正式 Review",
        Map.of("effect", "save_review"),
        Instant.parse("2026-06-26T00:00:00Z"),
        Instant.parse("2026-06-26T00:01:00Z"));
  }
}
