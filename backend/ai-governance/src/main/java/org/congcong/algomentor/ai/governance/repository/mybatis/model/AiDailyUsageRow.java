package org.congcong.algomentor.ai.governance.repository.mybatis.model;

import java.time.Instant;
import java.time.LocalDate;

public record AiDailyUsageRow(
    Long id,
    long userId,
    LocalDate quotaDate,
    String scope,
    long requestCount,
    long inputTokens,
    long outputTokens,
    long cachedTokens,
    long reasoningTokens,
    long totalTokens,
    long limitCount,
    Instant updatedAt
) {
}
