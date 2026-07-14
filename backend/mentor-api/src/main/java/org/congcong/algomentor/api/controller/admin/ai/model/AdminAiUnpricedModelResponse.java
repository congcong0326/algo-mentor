package org.congcong.algomentor.api.controller.admin.ai.model;

import java.time.Instant;

public record AdminAiUnpricedModelResponse(
    String provider,
    String model,
    long modelCallCount,
    long totalTokens,
    Instant lastSeenAt
) {
}
