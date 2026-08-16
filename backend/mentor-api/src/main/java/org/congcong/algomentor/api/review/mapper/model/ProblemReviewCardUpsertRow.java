package org.congcong.algomentor.api.review.mapper.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;

public record ProblemReviewCardUpsertRow(
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
    int lapses,
    Instant dueAt,
    Instant lastReviewedAt
) {
}
