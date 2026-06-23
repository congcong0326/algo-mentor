package org.congcong.algomentor.ai.governance.model;

import java.time.Instant;
import java.util.Map;

public record AiRunContext(
    String runId,
    AiActor actor,
    AiPurpose purpose,
    AiRunSource source,
    String idempotencyKey,
    int requestSize,
    boolean streaming,
    Map<String, Object> metadata,
    Instant createdAt
) {

  public AiRunContext {
    if (runId == null || runId.isBlank()) {
      throw new IllegalArgumentException("AI run id must not be blank");
    }
    if (actor == null) {
      throw new IllegalArgumentException("AI actor must not be null");
    }
    if (purpose == null) {
      throw new IllegalArgumentException("AI purpose must not be null");
    }
    if (source == null) {
      throw new IllegalArgumentException("AI run source must not be null");
    }
    if (requestSize < 0) {
      throw new IllegalArgumentException("AI request size must not be negative");
    }
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    createdAt = createdAt == null ? Instant.now() : createdAt;
  }
}
