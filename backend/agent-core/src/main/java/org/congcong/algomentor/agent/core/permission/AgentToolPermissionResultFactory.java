package org.congcong.algomentor.agent.core.permission;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Objects;
import org.congcong.algomentor.agent.core.runtime.model.AgentToolResultJsonKeys;
import org.congcong.algomentor.agent.core.runtime.model.AgentToolResultTypes;

public final class AgentToolPermissionResultFactory {

  public static final String REASON_USER_REJECTED = "user_rejected";
  public static final String REASON_TIMEOUT = "timeout";
  public static final String REASON_RUN_CANCELLED = "run_cancelled";
  public static final String REASON_POLICY_DENIED = "policy_denied";

  private static final String DEFAULT_DENIED_MESSAGE = "Tool execution was denied by the user.";
  private static final String DEFAULT_TIMEOUT_MESSAGE = "Waiting for tool permission timed out. Tool was not executed.";
  private static final String DEFAULT_CANCELLED_MESSAGE = "Run was cancelled. Tool was not executed.";
  private static final String DEFAULT_POLICY_DENIED_MESSAGE = "Tool execution was denied by policy.";

  private final ObjectMapper objectMapper;

  public AgentToolPermissionResultFactory(ObjectMapper objectMapper) {
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
  }

  public JsonNode denied(String toolName, String toolCallId, String permissionRequestId, String reason) {
    return denied(
        toolName,
        toolCallId,
        permissionRequestId,
        reason,
        DEFAULT_DENIED_MESSAGE);
  }

  public JsonNode denied(
      String toolName,
      String toolCallId,
      String permissionRequestId,
      String reason,
      String message
  ) {
    return result(
        AgentToolResultTypes.TOOL_PERMISSION_DENIED,
        toolName,
        toolCallId,
        permissionRequestId,
        normalizedText(reason, REASON_USER_REJECTED),
        normalizedText(message, DEFAULT_DENIED_MESSAGE),
        false);
  }

  public JsonNode policyDenied(String toolName, String toolCallId, String reason) {
    return policyDenied(
        toolName,
        toolCallId,
        reason,
        DEFAULT_POLICY_DENIED_MESSAGE);
  }

  public JsonNode policyDenied(String toolName, String toolCallId, String reason, String message) {
    return result(
        AgentToolResultTypes.TOOL_PERMISSION_DENIED,
        toolName,
        toolCallId,
        null,
        normalizedText(reason, REASON_POLICY_DENIED),
        normalizedText(message, DEFAULT_POLICY_DENIED_MESSAGE),
        false);
  }

  public JsonNode timeout(String toolName, String toolCallId, String permissionRequestId) {
    return result(
        AgentToolResultTypes.TOOL_PERMISSION_TIMEOUT,
        toolName,
        toolCallId,
        permissionRequestId,
        REASON_TIMEOUT,
        DEFAULT_TIMEOUT_MESSAGE,
        true);
  }

  public JsonNode cancelled(String toolName, String toolCallId, String permissionRequestId) {
    return result(
        AgentToolResultTypes.TOOL_PERMISSION_DENIED,
        toolName,
        toolCallId,
        permissionRequestId,
        REASON_RUN_CANCELLED,
        DEFAULT_CANCELLED_MESSAGE,
        false);
  }

  private ObjectNode result(
      String type,
      String toolName,
      String toolCallId,
      String permissionRequestId,
      String reason,
      String message,
      boolean retryable
  ) {
    requireText(toolName, "Agent tool permission result tool name must not be blank");
    requireText(toolCallId, "Agent tool permission result tool call id must not be blank");
    requireText(reason, "Agent tool permission result reason must not be blank");
    requireText(message, "Agent tool permission result message must not be blank");

    ObjectNode result = objectMapper.createObjectNode();
    result.put(AgentToolResultJsonKeys.TYPE, type);
    result.put(AgentToolResultJsonKeys.TOOL_NAME, toolName);
    result.put(AgentToolResultJsonKeys.TOOL_CALL_ID, toolCallId);
    if (permissionRequestId != null && !permissionRequestId.isBlank()) {
      result.put(AgentToolResultJsonKeys.PERMISSION_REQUEST_ID, permissionRequestId);
    } else {
      result.putNull(AgentToolResultJsonKeys.PERMISSION_REQUEST_ID);
    }
    result.put(AgentToolResultJsonKeys.MESSAGE, message);
    result.put(AgentToolResultJsonKeys.REASON, reason);
    result.put(AgentToolResultJsonKeys.RETRYABLE, retryable);
    return result;
  }

  private static String normalizedText(String value, String fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    return value;
  }

  private static void requireText(String value, String message) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(message);
    }
  }
}
