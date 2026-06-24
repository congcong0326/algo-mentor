package org.congcong.algomentor.api.practice.mapper.model;

import java.time.Instant;

public record PracticeSessionRow(
    long id,
    long userId,
    long planId,
    int phaseIndex,
    String problemSlug,
    String status,
    Long agentTaskId,
    Long problemStatementMessageId,
    String progressStatus,
    Instant lastMessageAt,
    Instant createdAt,
    Instant updatedAt,
    String locale
) {
}
