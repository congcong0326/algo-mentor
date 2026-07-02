package org.congcong.algomentor.api.review.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record MistakeNoteResponse(
    long id,
    String problemSlug,
    String problemTitle,
    String problemLocale,
    String problemDifficulty,
    String source,
    Map<String, Object> sourceDetail,
    String masteryState,
    int repetitions,
    BigDecimal easeFactor,
    int intervalDays,
    Instant dueAt,
    int lapses,
    Instant lastReviewedAt,
    String lastGrade,
    boolean archived,
    String userNotePersistent,
    Instant createdAt,
    Instant updatedAt
) {
}
