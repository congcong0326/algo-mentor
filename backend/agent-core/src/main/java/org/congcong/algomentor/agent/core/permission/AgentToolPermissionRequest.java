package org.congcong.algomentor.agent.core.permission;

import java.time.Instant;
import java.util.Map;

public record AgentToolPermissionRequest(
    String permissionRequestId,
    String runId,
    int stepIndex,
    String toolCallId,
    String toolName,
    String displayName,
    String reason,
    Map<String, Object> preview,
    Instant createdAt,
    Instant expiresAt
) {

  public AgentToolPermissionRequest {
    requireText(permissionRequestId, "Agent tool permission request id must not be blank");
    requireText(runId, "Agent tool permission run id must not be blank");
    if (stepIndex < 1) {
      throw new IllegalArgumentException("Agent tool permission request step index must be positive");
    }
    requireText(toolCallId, "Agent tool permission tool call id must not be blank");
    requireText(toolName, "Agent tool permission tool name must not be blank");
    requireText(displayName, "Agent tool permission request display name must not be blank");
    requireText(reason, "Agent tool permission request reason must not be blank");
    preview = preview == null ? Map.of() : Map.copyOf(preview);
    if (preview.isEmpty()) {
      throw new IllegalArgumentException("Agent tool permission request preview must not be empty");
    }
    if (createdAt == null) {
      throw new IllegalArgumentException("Agent tool permission request created time must not be null");
    }
    if (expiresAt == null) {
      throw new IllegalArgumentException("Agent tool permission request expiry time must not be null");
    }
    if (!expiresAt.isAfter(createdAt)) {
      throw new IllegalArgumentException("Agent tool permission request expiry time must be after created time");
    }
  }

  private static void requireText(String value, String message) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(message);
    }
  }
}
