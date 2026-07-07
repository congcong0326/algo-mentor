package org.congcong.algomentor.api.practice.mapper.model;

import java.time.Instant;

public record PracticeProgressRow(
    long id,
    long userId,
    long planId,
    int phaseIndex,
    String problemSlug,
    String status,
    Instant startedAt,
    Instant completedAt,
    Instant skippedAt,
    Instant createdAt,
    Instant updatedAt
) {
}
