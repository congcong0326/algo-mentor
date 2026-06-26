package org.congcong.algomentor.agent.core.permission;

import java.util.Map;

public record AgentToolPermissionDecisionPlan(
    AgentToolPermissionBehavior behavior,
    String displayName,
    String reason,
    Map<String, Object> preview,
    String policySource,
    Map<String, Object> metadata
) {

  private static final String DEFAULT_POLICY_SOURCE = "default";

  public AgentToolPermissionDecisionPlan(
      AgentToolPermissionBehavior behavior,
      String displayName,
      String reason,
      Map<String, Object> preview,
      String policySource
  ) {
    this(behavior, displayName, reason, preview, policySource, Map.of());
  }

  public AgentToolPermissionDecisionPlan {
    if (behavior == null) {
      throw new IllegalArgumentException("Agent tool permission behavior must not be null");
    }
    preview = preview == null ? Map.of() : Map.copyOf(preview);
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    policySource = normalizedPolicySource(policySource);
    validateForBehavior(behavior, displayName, reason, preview, metadata);
  }

  public static AgentToolPermissionDecisionPlan allow(String policySource) {
    return allow(policySource, Map.of());
  }

  public static AgentToolPermissionDecisionPlan allow(String policySource, Map<String, Object> metadata) {
    return new AgentToolPermissionDecisionPlan(
        AgentToolPermissionBehavior.ALLOW,
        null,
        null,
        Map.of(),
        policySource,
        metadata);
  }

  public static AgentToolPermissionDecisionPlan deny(String reason, String policySource) {
    return deny(reason, policySource, Map.of());
  }

  public static AgentToolPermissionDecisionPlan deny(
      String reason,
      String policySource,
      Map<String, Object> metadata
  ) {
    return new AgentToolPermissionDecisionPlan(
        AgentToolPermissionBehavior.DENY,
        null,
        reason,
        Map.of(),
        policySource,
        metadata);
  }

  public static AgentToolPermissionDecisionPlan ask(
      String displayName,
      String reason,
      Map<String, Object> preview,
      String policySource
  ) {
    return ask(displayName, reason, preview, policySource, Map.of());
  }

  public static AgentToolPermissionDecisionPlan ask(
      String displayName,
      String reason,
      Map<String, Object> preview,
      String policySource,
      Map<String, Object> metadata
  ) {
    return new AgentToolPermissionDecisionPlan(
        AgentToolPermissionBehavior.ASK,
        displayName,
        reason,
        preview,
        policySource,
        metadata);
  }

  public static AgentToolPermissionDecisionPlan passthrough() {
    return new AgentToolPermissionDecisionPlan(
        AgentToolPermissionBehavior.PASSTHROUGH,
        null,
        null,
        Map.of(),
        DEFAULT_POLICY_SOURCE,
        Map.of());
  }

  private static String normalizedPolicySource(String value) {
    if (value == null || value.isBlank()) {
      return DEFAULT_POLICY_SOURCE;
    }
    return value;
  }

  private static void validateForBehavior(
      AgentToolPermissionBehavior behavior,
      String displayName,
      String reason,
      Map<String, Object> preview,
      Map<String, Object> metadata
  ) {
    if (behavior == AgentToolPermissionBehavior.ASK) {
      requireText(displayName, "Agent tool permission ask display name must not be blank");
      requireText(reason, "Agent tool permission ask reason must not be blank");
      if (preview.isEmpty()) {
        throw new IllegalArgumentException("Agent tool permission ask preview must not be empty");
      }
    }
    if (behavior == AgentToolPermissionBehavior.DENY) {
      requireText(reason, "Agent tool permission deny reason must not be blank");
    }
    if (behavior == AgentToolPermissionBehavior.PASSTHROUGH) {
      if (displayName != null || reason != null || !preview.isEmpty() || !metadata.isEmpty()) {
        throw new IllegalArgumentException("Agent tool permission passthrough must not contain decision details");
      }
    }
  }

  private static void requireText(String value, String message) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(message);
    }
  }
}
