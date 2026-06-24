package org.congcong.algomentor.mentor.application.practice;

import java.time.Instant;

public record PracticeProgress(
    long id,
    long userId,
    long planId,
    int phaseIndex,
    String problemSlug,
    PracticeProgressStatus status,
    Instant createdAt,
    Instant updatedAt
) {
}
