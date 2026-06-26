package org.congcong.algomentor.agent.core.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.congcong.algomentor.agent.core.runtime.model.AgentToolResultJsonKeys;
import org.congcong.algomentor.agent.core.runtime.model.AgentToolResultTypes;
import org.junit.jupiter.api.Test;

class AgentToolPermissionResultFactoryTest {

  private final AgentToolPermissionResultFactory factory =
      new AgentToolPermissionResultFactory(new ObjectMapper());

  @Test
  void deniedResultUsesStableNonRetryableShape() {
    JsonNode result = factory.denied(
        "submit_practice_code_review",
        "call_1",
        "perm_123",
        AgentToolPermissionResultFactory.REASON_USER_REJECTED);

    assertThat(result.get(AgentToolResultJsonKeys.TYPE).asText())
        .isEqualTo(AgentToolResultTypes.TOOL_PERMISSION_DENIED);
    assertThat(result.get(AgentToolResultJsonKeys.TOOL_NAME).asText())
        .isEqualTo("submit_practice_code_review");
    assertThat(result.get(AgentToolResultJsonKeys.TOOL_CALL_ID).asText()).isEqualTo("call_1");
    assertThat(result.get(AgentToolResultJsonKeys.PERMISSION_REQUEST_ID).asText()).isEqualTo("perm_123");
    assertThat(result.get(AgentToolResultJsonKeys.MESSAGE).asText()).contains("Tool");
    assertThat(result.get(AgentToolResultJsonKeys.REASON).asText())
        .isEqualTo(AgentToolPermissionResultFactory.REASON_USER_REJECTED);
    assertThat(result.get(AgentToolResultJsonKeys.RETRYABLE).asBoolean()).isFalse();
  }

  @Test
  void timeoutResultIsRetryable() {
    JsonNode result = factory.timeout("submit_practice_code_review", "call_1", "perm_123");

    assertThat(result.get(AgentToolResultJsonKeys.TYPE).asText())
        .isEqualTo(AgentToolResultTypes.TOOL_PERMISSION_TIMEOUT);
    assertThat(result.get(AgentToolResultJsonKeys.PERMISSION_REQUEST_ID).asText()).isEqualTo("perm_123");
    assertThat(result.get(AgentToolResultJsonKeys.REASON).asText())
        .isEqualTo(AgentToolPermissionResultFactory.REASON_TIMEOUT);
    assertThat(result.get(AgentToolResultJsonKeys.RETRYABLE).asBoolean()).isTrue();
  }

  @Test
  void cancelledResultExplainsRunCancellationWithoutRetry() {
    JsonNode result = factory.cancelled("submit_practice_code_review", "call_1", "perm_123");

    assertThat(result.get(AgentToolResultJsonKeys.TYPE).asText())
        .isEqualTo(AgentToolResultTypes.TOOL_PERMISSION_DENIED);
    assertThat(result.get(AgentToolResultJsonKeys.MESSAGE).asText()).contains("cancelled");
    assertThat(result.get(AgentToolResultJsonKeys.REASON).asText())
        .isEqualTo(AgentToolPermissionResultFactory.REASON_RUN_CANCELLED);
    assertThat(result.get(AgentToolResultJsonKeys.RETRYABLE).asBoolean()).isFalse();
  }

  @Test
  void policyDeniedKeepsStablePermissionRequestFieldWithoutPendingRequest() {
    JsonNode result = factory.policyDenied(
        "submit_practice_code_review",
        "call_1",
        "permission_hook_failed");

    assertThat(result.get(AgentToolResultJsonKeys.TYPE).asText())
        .isEqualTo(AgentToolResultTypes.TOOL_PERMISSION_DENIED);
    assertThat(result.get(AgentToolResultJsonKeys.PERMISSION_REQUEST_ID).isNull()).isTrue();
    assertThat(result.get(AgentToolResultJsonKeys.REASON).asText()).isEqualTo("permission_hook_failed");
    assertThat(result.get(AgentToolResultJsonKeys.RETRYABLE).asBoolean()).isFalse();
  }

  @Test
  void syntheticResultNeverContainsToolArguments() {
    JsonNode result = factory.denied(
        "submit_practice_code_review",
        "call_1",
        "perm_123",
        AgentToolPermissionResultFactory.REASON_USER_REJECTED,
        "Tool was not executed.");

    assertThat(result.toString())
        .doesNotContain("arguments")
        .doesNotContain("sourceCode")
        .doesNotContain("secret-token");
  }

  @Test
  void validatesRequiredFields() {
    assertThatThrownBy(() -> new AgentToolPermissionResultFactory(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("objectMapper");
    assertThatThrownBy(() -> factory.timeout(" ", "call_1", "perm_123"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tool name");
    assertThatThrownBy(() -> factory.cancelled("tool", "", "perm_123"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tool call id");
  }

  @Test
  void deniedResultFallsBackToUserRejectedReasonWhenReasonIsBlank() {
    JsonNode result = factory.denied("tool", "call_1", "perm_123", " ");

    assertThat(result.get(AgentToolResultJsonKeys.REASON).asText())
        .isEqualTo(AgentToolPermissionResultFactory.REASON_USER_REJECTED);
  }

  @Test
  void exposesPermissionResultTypeConstants() {
    assertThat(AgentToolResultTypes.TOOL_PERMISSION_DENIED).isEqualTo("tool_permission_denied");
    assertThat(AgentToolResultTypes.TOOL_PERMISSION_TIMEOUT).isEqualTo("tool_permission_timeout");
  }
}
