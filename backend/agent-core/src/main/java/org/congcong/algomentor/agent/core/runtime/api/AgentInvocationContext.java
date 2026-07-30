package org.congcong.algomentor.agent.core.runtime.api;

import java.util.Objects;

/** 由业务入口构造且由 Runtime 信任的单次调用上下文。 */
public record AgentInvocationContext(
    long userId,
    AgentInvocationMode mode,
    String idempotencyKey,
    String parentRunId,
    Integer parentStepIndex,
    int requestSize,
    boolean streaming
) {

  public AgentInvocationContext {
    if (userId < 1) {
      throw new IllegalArgumentException("Agent invocation user id must be positive");
    }
    mode = Objects.requireNonNull(mode, "Agent invocation mode must not be null");
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new IllegalArgumentException("Agent invocation idempotency key must not be blank");
    }
    idempotencyKey = idempotencyKey.trim();
    if (parentRunId != null && parentRunId.isBlank()) {
      throw new IllegalArgumentException("Agent invocation parent run id must not be blank");
    }
    if (parentRunId == null && parentStepIndex != null) {
      throw new IllegalArgumentException("Agent invocation parent step requires a parent run id");
    }
    if (parentStepIndex != null && parentStepIndex < 1) {
      throw new IllegalArgumentException("Agent invocation parent step index must be positive");
    }
    if (requestSize < 0) {
      throw new IllegalArgumentException("Agent invocation request size must not be negative");
    }
  }
}
