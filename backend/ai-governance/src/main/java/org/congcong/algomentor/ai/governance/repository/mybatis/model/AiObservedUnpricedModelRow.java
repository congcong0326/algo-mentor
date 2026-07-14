package org.congcong.algomentor.ai.governance.repository.mybatis.model;

import java.time.Instant;

public record AiObservedUnpricedModelRow(
    String provider,
    String model,
    long modelCallCount,
    long totalTokens,
    Instant lastSeenAt
) {
}
