package org.congcong.algomentor.api.review.mapper.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;

public record ProblemReviewCardRow(
    long id,
    long userId,
    String problemSlug,
    String source,
    JsonNode sourceDetailJson,
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
