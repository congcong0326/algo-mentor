package org.congcong.algomentor.ai.governance.adminquery;

import java.time.Instant;

/** 已实际出现、但当前没有启用价格的模型。 */
public record AiObservedUnpricedModel(
    String provider,
    String model,
    long modelCallCount,
    long totalTokens,
    Instant lastSeenAt
) {
}
