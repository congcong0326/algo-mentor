package org.congcong.algomentor.api.review.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record ReviewCardResponse(
    long id,
    String problemSlug,
    String problemTitle,
    String problemDifficulty,
    String source,
    Map<String, Object> sourceDetail,
    int repetitions,
    int intervalDays,
    String fsrsState,
    Integer fsrsStep,
    BigDecimal fsrsStability,
    BigDecimal fsrsDifficulty,
    Instant dueAt,
    int lapses,
    Instant lastReviewedAt,
    String lastRating,
    boolean archived,
    Instant createdAt,
    Instant updatedAt
) {
}
