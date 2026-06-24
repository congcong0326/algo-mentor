package org.congcong.algomentor.api.practice.model;

import java.time.Instant;

public record PracticeSessionSummaryResponse(
    long id,
    long planId,
    int phaseIndex,
    String problemSlug,
    String progressStatus,
    long agentTaskId,
    Instant createdAt,
    Instant updatedAt) {
}
