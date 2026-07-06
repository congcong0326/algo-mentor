package org.congcong.algomentor.api.review.mapper.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;

public record MistakeNoteRow(
    long id,
    long userId,
    String problemSlug,
    String source,
    JsonNode sourceDetailJson,
    Long originPlanId,
    Integer originPhaseIndex,
    Long originPracticeSessionId,
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
    String userNotePersistent,
    JsonNode pendingCardJson,
    String pendingCardVariant,
    String pendingCardSignature,
    Instant pendingCardGeneratedAt,
    Instant createdAt,
    Instant updatedAt
) {
}
